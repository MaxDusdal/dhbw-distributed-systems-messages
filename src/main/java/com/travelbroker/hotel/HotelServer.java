package com.travelbroker.hotel;

import com.travelbroker.dto.HotelRequest;
import com.travelbroker.dto.HotelResponse;
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.ConfigProvider;
import com.travelbroker.util.JsonUtil;
import com.travelbroker.util.Simulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HotelServer implements AutoCloseable {
    private static final String CONFIG_AVERAGE_PROCESSING_TIME = "AVERAGE_PROCESSING_TIME";
    private static final String CONFIG_FAILURE_PROBABILITY = "BOOKING_FAILURE_PROBABILITY";
    private static final String CONFIG_LOSS_PROBABILITY = "MESSAGE_LOSS_PROBABILITY";

    private static final Logger logger = LoggerFactory.getLogger(HotelServer.class);

    private final Hotel hotel;
    private final ZeroMQClient backend;
    private final Properties config;

    /**
     * time-block → list of bookingIds
     */
    private final Map<Integer, List<UUID>> bookings = new HashMap<>();

    /**
     * isolate business work from the ZeroMQ listener thread
     */
    private final ExecutorService pool;

    public HotelServer(Hotel hotel, String backendEndpoint) {
        this.hotel = Objects.requireNonNull(hotel);
        this.config = ConfigProvider.loadConfiguration();

        pool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("business-", 0).factory());

        backend = new ZeroMQClient(backendEndpoint, SocketType.DEALER);
        backend.connect();
        backend.sendRequest("READY");                // tell broker we are idle

        backend.listenForResponses(this::handleMessage);
    }

    public static void main(String[] args) throws Exception {
        Hotel demo = new Hotel("DemoHotel", "DemoHotel", 10);
        try (HotelServer ignored = new HotelServer(demo, "tcp://localhost:5556")) {
            Thread.currentThread().join();
        }
    }

    private void handleMessage(String frame) {
        // frame = CLIENT_ID \0\0 json
        String[] parts = frame.split("\0\0", 2);
        if (parts.length != 2) {
            logger.warn("Malformed frame (missing delimiter) {}", frame);
            return;
        }
        String clientId = parts[0];
        String body = parts[1];

        pool.submit(() -> {
            HotelRequest request = JsonUtil.fromJson(body, HotelRequest.class);
            HotelBooking booking = request.getBooking();
            HotelResponse response;

            switch (request.getAction()) {
                case BOOK -> response = tryBook(booking);
                case CANCEL -> response = tryCancel(booking);
                default -> response = new HotelResponse(
                        booking.getBookingId(), false,
                        "Unknown action: " + request.getAction());
            }
            // Message loss after local state change
            if (Simulation.artificialFailure(Double.parseDouble(config.getProperty(CONFIG_FAILURE_PROBABILITY, "0.0")))) {
                logger.info("Simulated message loss for {} of booking {}", request.getAction(), booking.getBookingId());
                return;
            }
            backend.sendRequest(clientId + "\0\0" + JsonUtil.toJson(response));
        });
    }

    private HotelResponse tryBook(HotelBooking booking) {
        Simulation.artificialLatency(
                Integer.parseInt(config.getProperty(CONFIG_AVERAGE_PROCESSING_TIME, "0")));

        synchronized (this) {
            List<UUID> list = bookings.computeIfAbsent(booking.getTimeBlock(), k -> new ArrayList<>());
            if (list.size() >= hotel.getTotalRooms()) {
                return new HotelResponse(booking.getBookingId(), false, "No rooms available");
            }
            if (list.contains(booking.getBookingId())) {
                return new HotelResponse(booking.getBookingId(), false, "Booking already exists");
            }
            if (Simulation.artificialFailure(Double.parseDouble(config.getProperty(CONFIG_FAILURE_PROBABILITY, "0.0")))) {
                return new HotelResponse(booking.getBookingId(), false, "Simulated internal failure");
            }
            list.add(booking.getBookingId());
            logger.info("Booked {} at hotel {} block {}", booking.getBookingId(),
                    hotel.getId(), booking.getTimeBlock());
        }
        return new HotelResponse(booking.getBookingId(), true, null);
    }

    private HotelResponse tryCancel(HotelBooking booking) {
        synchronized (this) {
            List<UUID> list = bookings.get(booking.getTimeBlock());
            if (list == null || !list.remove(booking.getBookingId())) {
                return new HotelResponse(booking.getBookingId(), false, "No booking to cancel");
            }
            logger.info("Cancelled {} at hotel {} block {}", booking.getBookingId(),
                    hotel.getId(), booking.getTimeBlock());
        }
        return new HotelResponse(booking.getBookingId(), true, null);
    }

    @Override
    public void close() {
        pool.shutdownNow();
        backend.close();
    }
}
