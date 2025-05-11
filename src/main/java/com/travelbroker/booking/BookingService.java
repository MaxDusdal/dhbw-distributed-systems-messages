package com.travelbroker.booking;

import com.travelbroker.broker.TravelBroker;
import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.dto.HotelRequest;
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelAction;
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
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private static final String CONFIG_REQUEST_RATE = "BOOKING_REQUEST_ARRIVAL_RATE";
    private static final int MAX_TIME_BLOCK = 100;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final int instanceId = COUNTER.incrementAndGet();
    private final ZeroMQClient client;
    private final ScheduledExecutorService scheduler;
    private final List<Hotel> hotels;
    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();

    // Store completed requests for overview
    private final Map<UUID, CompletedRequest> completedRequests = new ConcurrentHashMap<>();

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

        this.client = new ZeroMQClient(TravelBroker.getClientEndpoint(), SocketType.DEALER);

        Properties config = ConfigProvider.getConfiguration();
        int totalRpm = Integer.parseInt(config.getProperty(ConfigProvider.BOOKING_REQUEST_ARRIVAL_RATE, "60"));
        this.rpm = Math.max(1, totalRpm / totalInstances);
        this.interval = Duration.ofMinutes(1).toMillis() / rpm;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "booking-gen-" + instanceId));

        logger.debug("BookingService#{} rpm={}  interval={}ms  hotels={}  broker={}",
                instanceId, rpm, interval, hotels.size(), TravelBroker.getClientEndpoint());
    }

    private static BookingRequest toDto(TripBooking tripBooking) {
        HotelRequest[] hotelRequests = Arrays.stream(tripBooking.getHotelBookings())
                .map(hotelBooking -> new HotelRequest(
                        hotelBooking,
                        HotelAction.BOOK))
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

        // Generate and log the overview report
        generateRequestOverview();

        logger.info("BookingService#{} stopped", instanceId);
    }

    /**
     * Generates and sends a random booking request to the broker.
     * 
     * This method:
     * 1. Generates a random trip booking
     * 2. Converts it to a DTO and JSON format
     * 3. Stores it as a pending request
     * 4. Sends it to the broker via the client
     * 5. Handles success/failure of the send operation
     */
    private void sendRandomBooking() {
        // Generate a random trip booking and get its ID
        TripBooking trip = generateRandomTripBooking();
        UUID bookingId = trip.getBookingId();

        // Convert the trip booking to DTO format and then to JSON
        BookingRequest dto = toDto(trip);
        String json = JsonUtil.toJson(dto);

        // Store as pending request to track its status
        pending.put(bookingId, new PendingRequest(trip));

        // Attempt to send the request to the broker
        boolean sent = client.sendRequest(json);
        if (!sent) {
            logger.error("BookingService#{} failed to dispatch {}", instanceId, bookingId);
            pending.remove(bookingId);
        } else {
            logger.info("BookingService#{} sent booking {}", instanceId, bookingId);
        }
    }

    /**
     * Handles the response received from the broker for a booking request.
     * 
     * This method:
     * 1. Parses the JSON response into a BookingResponse object
     * 2. Validates the response and its booking ID
     * 3. Retrieves and removes the corresponding pending request
     * 4. Updates the request status and calculates response time
     * 5. Logs the final outcome with timing information
     *
     * @param json The JSON string response received from the broker
     */
    private void handleResponse(String json) {
        try {
            // Parse JSON response into BookingResponse object
            BookingResponse response = JsonUtil.fromJson(json, BookingResponse.class);

            // Validate response and booking ID are not null
            if (response == null || response.getBookingId() == null) {
                logger.error("Malformed response {}", json);
                return;
            }

            // Retrieve and remove the pending request for this booking ID
            PendingRequest pendingRequest = pending.remove(response.getBookingId());

            // Check if we have a matching pending request, else log a warning that the
            // response is orphaned
            if (pendingRequest == null) {
                return;
            }

            // Update the request status and calculate round-trip time
            PendingRequest completedRequest = pendingRequest.complete(response.getStatus());
            long rtt = completedRequest.responseTime - completedRequest.requestTime;

            // Store the completed request for the overview
            completedRequests.put(response.getBookingId(), new CompletedRequest(
                    completedRequest.trip.getBookingId(),
                    response.getStatus(),
                    response.getMessage(),
                    rtt,
                    completedRequest.requestTime,
                    completedRequest.responseTime));

            // Log the completed booking with status, message and timing
            logger.info("\u001B[32m BookingService#{} finished {} status={} msg='{}' in {} ms \u001B[0m",
                    instanceId, response.getBookingId(), response.getStatus(),
                    response.getMessage(), rtt);

        } catch (Exception ex) {
            logger.error("Cannot parse broker response {}", json, ex);
        }
    }

    /**
     * Generates a random trip booking with sequential hotel stays.
     * 
     * The generated trip will have:
     * - A random customer ID
     * - Between 1-5 hotel bookings in sequence
     * - Each hotel booking will be for consecutive time blocks
     * - No two consecutive bookings will be at the same hotel
     * - Time blocks are chosen to ensure all bookings fit within MAX_TIME_BLOCK
     *
     * @return A new TripBooking with random but valid hotel bookings
     */
    private TripBooking generateRandomTripBooking() {
        // Generate a random UUID to identify the customer
        UUID customerId = UUID.randomUUID();

        // Create between 1-5 hotel bookings (segments) for this trip
        int segments = ThreadLocalRandom.current().nextInt(1, 6);
        HotelBooking[] bookings = new HotelBooking[segments];

        // Pick initial time block ensuring all segments will fit before MAX_TIME_BLOCK
        // For example, if segments=3 and MAX_TIME_BLOCK=10, timeBlock will be between
        // 1-8
        int timeBlock = ThreadLocalRandom.current().nextInt(1, MAX_TIME_BLOCK - segments + 1);

        // Track previous hotel to avoid booking same hotel twice in a row
        Hotel prev = null;

        // Generate each hotel booking in sequence
        for (int i = 0; i < segments; i++) {
            Hotel h;
            // Keep selecting random hotels until we find one different from previous
            do {
                h = hotels.get(ThreadLocalRandom.current().nextInt(hotels.size()));
            } while (h.equals(prev)); // avoid consecutive identical hotels
            prev = h;

            // Create booking with unique ID at selected hotel for current time block
            bookings[i] = new HotelBooking(UUID.randomUUID(), h.getId(), timeBlock++);
        }

        // Package all bookings into a single trip
        return new TripBooking(bookings, customerId);
    }

    /**
     * Generates and logs an overview of all processed booking requests
     */
    private void generateRequestOverview() {
        if (completedRequests.isEmpty()) {
            logger.info("No booking requests were completed during this session");
            return;
        }

        long pendingRequests = pending.size();
        // Calculate statistics
        long totalRequests = completedRequests.size();
        long successfulRequests = completedRequests.values().stream()
                .filter(req -> "CONFIRMED".equals(req.status))
                .count();
        long failedRequests = totalRequests - successfulRequests;
        double successRate = (double) successfulRequests / totalRequests * 100;

        // Calculate average response time
        double avgResponseTime = completedRequests.values().stream()
                .mapToLong(CompletedRequest::responseTimeMs)
                .average()
                .orElse(0);

        // Find min and max response times
        long minResponseTime = completedRequests.values().stream()
                .mapToLong(CompletedRequest::responseTimeMs)
                .min()
                .orElse(0);

        long maxResponseTime = completedRequests.values().stream()
                .mapToLong(CompletedRequest::responseTimeMs)
                .max()
                .orElse(0);

        // Build the overview report
        StringBuilder report = new StringBuilder();
        report.append("\n\n")
                .append("=".repeat(80)).append("\n")
                .append(" BOOKING SERVICE REQUEST OVERVIEW \n")
                .append("=".repeat(80)).append("\n\n")
                .append(String.format("Total Requests:     %d\n", totalRequests))
                .append(String.format("Pending Requests:   %d\n", pendingRequests))
                .append(String.format("Successful:         %d (%.1f%%)\n", successfulRequests, successRate))
                .append(String.format("Failed:             %d (%.1f%%)\n", failedRequests, 100 - successRate))
                .append(String.format("Avg Response Time:  %.2f ms\n", avgResponseTime))
                .append(String.format("Min Response Time:  %d ms\n", minResponseTime))
                .append(String.format("Max Response Time:  %d ms\n", maxResponseTime))
                .append("\nStatus Distribution:\n");

        // Count by status
        Map<String, Long> statusCounts = new HashMap<>();
        completedRequests.values().forEach(req -> statusCounts.merge(req.status, 1L, Long::sum));

        // Add status distribution to report
        statusCounts.forEach((status, count) -> report.append(String.format("  %-15s %d (%.1f%%)\n",
                status + ":", count, (double) count / totalRequests * 100)));

        report.append("\n").append("=".repeat(80)).append("\n");

        // Log the report with different colors based on success rate
        String colorCode = successRate >= 90 ? "\u001B[32m" : // green for >=90%
                successRate >= 70 ? "\u001B[33m" : // yellow for >=70%
                        "\u001B[31m"; // red for <70%

        logger.info("{}{}\u001B[0m", colorCode, report);
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
    }

    /**
     * Record to store information about completed requests for reporting
     */
    private record CompletedRequest(
            UUID bookingId,
            String status,
            String message,
            long responseTimeMs,
            long requestTimeMs,
            long responseReceivedTimeMs) {
    }
}
