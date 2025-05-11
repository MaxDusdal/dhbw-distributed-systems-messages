package com.travelbroker.booking;

import com.travelbroker.broker.TravelBroker;
import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.dto.HotelRequest;
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import com.travelbroker.model.TripBooking;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.ConfigProvider;
import com.travelbroker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically generates booking requests and processes asynchronous replies.
 */
public final class BookingService implements AutoCloseable {

    // ── constants ────────────────────────────────────────────────────
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private static final String CONFIG_REQUEST_RATE = "BOOKING_REQUEST_ARRIVAL_RATE";
    private static final int MAX_TIME_BLOCK = 100;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final int instanceId = COUNTER.incrementAndGet();
    private final ZeroMQClient client;
    private final ScheduledExecutorService scheduler;
    private final List<Hotel> hotels;
    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();

    private final int rpm; // requests per minute for THIS instance
    private final long interval; // milliseconds between requests

    private volatile boolean running;

    public BookingService(TravelBroker broker,
            int totalInstances,
            List<Hotel> availableHotels) {

        Objects.requireNonNull(broker, "broker");
        Objects.requireNonNull(availableHotels, "availableHotels");
        if (availableHotels.isEmpty()) {
            throw new IllegalArgumentException("No hotels provided");
        }
        this.hotels = List.copyOf(availableHotels);

        this.client = new ZeroMQClient(broker.getClientEndpoint(), SocketType.DEALER);

        Properties config = ConfigProvider.loadConfiguration();
        int totalRpm = Integer.parseInt(config.getProperty(CONFIG_REQUEST_RATE, "60"));
        this.rpm = Math.max(1, totalRpm / totalInstances);
        this.interval = Duration.ofMinutes(1).toMillis() / rpm;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "booking-gen-" + instanceId));

        logger.info("BookingService#{} rpm={}  interval={}ms  hotels={}  broker={}",
                instanceId, rpm, interval, hotels.size(), broker.getClientEndpoint());
    }

    private static BookingRequest toDto(TripBooking tripBooking) {
        HotelRequest[] hotelRequests = Arrays.stream(tripBooking.getHotelBookings())
                .map(hotelBooking -> new HotelRequest(
                        hotelBooking,
                        HotelRequest.Action.BOOK))
                .toArray(HotelRequest[]::new);

        return new BookingRequest(
                tripBooking.getBookingId(),
                tripBooking.getCustomerId(),
                tripBooking.getStatus().name(),
                hotelRequests);
    }

    private static void shutdownExecutor(ExecutorService ex, long timeout, TimeUnit unit) {
        ex.shutdown();
        try {
            if (!ex.awaitTermination(timeout, unit))
                ex.shutdownNow();
        } catch (InterruptedException ie) {
            ex.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        if (running)
            return;
        running = true;

        client.connect();
        client.listenForResponses(this::handleResponse);

        scheduler.scheduleAtFixedRate(this::sendRandomBooking,
                0, interval, TimeUnit.MILLISECONDS);

        logger.info("BookingService#{} started", instanceId);
    }

    public void stop() {
        if (!running)
            return;
        running = false;
        shutdownExecutor(scheduler, 5, TimeUnit.SECONDS);
        logger.info("BookingService#{} stopped", instanceId);
    }

    private void sendRandomBooking() {
        TripBooking trip = generateRandomTripBooking();
        UUID bookingId = trip.getBookingId();

        BookingRequest dto = toDto(trip);
        String json = JsonUtil.toJson(dto);

        pending.put(bookingId, new PendingRequest(trip));

        boolean sent = client.sendRequest(json);
        if (!sent) {
            logger.error("BookingService#{} failed to dispatch {}", instanceId, bookingId);
            pending.remove(bookingId);
        } else {
            logger.debug("BookingService#{} sent booking {}", instanceId, bookingId);
        }
    }

    private void handleResponse(String json) {
        try {
            BookingResponse response = JsonUtil.fromJson(json, BookingResponse.class);
            if (response == null || response.getBookingId() == null) {
                logger.error("Malformed response {}", json);
                return;
            }

            PendingRequest pendingRequest = pending.remove(response.getBookingId());
            if (pendingRequest == null) {
                logger.warn("Orphan response for {}", response.getBookingId());
                return;
            }

            pendingRequest.complete(response.getStatus());
            long rtt = pendingRequest.responseTime() - pendingRequest.requestTime();
            logger.info("\u001B[32m BookingService#{} finished {} status={} msg='{}' in {} ms \u001B[0m",
                    instanceId, response.getBookingId(), response.getStatus(),
                    response.getMessage(), rtt);

        } catch (Exception ex) {
            logger.error("Cannot parse broker response {}", json, ex);
        }
    }

    private TripBooking generateRandomTripBooking() {
        UUID customerId = UUID.randomUUID();
        int segments = ThreadLocalRandom.current().nextInt(1, 6);
        HotelBooking[] bookings = new HotelBooking[segments];

        int timeBlock = ThreadLocalRandom.current().nextInt(1, MAX_TIME_BLOCK - segments + 1); // Ensure all segments
                                                                                               // lie within the max
                                                                                               // time block
        Hotel prev = null;

        for (int i = 0; i < segments; i++) {
            Hotel h;
            do {
                h = hotels.get(ThreadLocalRandom.current().nextInt(hotels.size()));
            } while (h.equals(prev)); // avoid consecutive identical hotels
            prev = h;

            bookings[i] = new HotelBooking(UUID.randomUUID(), h.getId(), timeBlock++);
        }
        return new TripBooking(bookings, customerId);
    }

    @Override
    public void close() {
        stop();
        client.close();
    }

    private record PendingRequest(TripBooking trip,
            long requestTime,
            String status,
            long responseTime) {

        PendingRequest(TripBooking trip) {
            this(trip, System.currentTimeMillis(), null, 0);
        }

        PendingRequest complete(String newStatus) {
            return new PendingRequest(trip, requestTime, newStatus, System.currentTimeMillis());
        }

        public long requestTime() {
            return requestTime;
        }

        public long responseTime() {
            return responseTime;
        }
    }
}
