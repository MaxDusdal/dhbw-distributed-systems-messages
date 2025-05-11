package com.travelbroker.hotel;

import com.travelbroker.broker.TravelBroker;
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

    public HotelServer(Hotel hotel) {
        this.hotel = Objects.requireNonNull(hotel);
        this.config = ConfigProvider.loadConfiguration();

        pool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("business-", 0).factory());

        backend = new ZeroMQClient(TravelBroker.getBackendEndpoint(), SocketType.DEALER);
        backend.connect();
        backend.sendRequest("READY:" + hotel.getId()); // tell broker that system is idle

        backend.listenForResponses(this::handleMessage);
    }

    private void handleMessage(String json) {
        pool.submit(() -> {
            HotelRequest request = JsonUtil.fromJson(json, HotelRequest.class);
            HotelResponse response;

            switch (request.getAction()) {
                case BOOK -> response = tryBook(request);
                case CANCEL -> response = tryCancel(request);
                default -> response = new HotelResponse(
                        request.getRequestID(), false,
                        "Unknown action: " + request.getAction());
            }
            // Message loss after local state change
            if (Simulation.artificialFailure(Double.parseDouble(config.getProperty(CONFIG_LOSS_PROBABILITY, "0.0")))) {
                logger.info("Simulated message loss for {} of booking {}", request.getAction(), request.getRequestID());
                return;
            }
            backend.sendRequest(JsonUtil.toJson(response));
        });
    }

    private HotelResponse tryBook(HotelRequest request) {
        HotelBooking booking = request.getBooking();
        Simulation.artificialLatency(
                Integer.parseInt(config.getProperty(CONFIG_AVERAGE_PROCESSING_TIME, "0")));

        synchronized (this) {
            List<UUID> list = bookings.computeIfAbsent(booking.getTimeBlock(), k -> new ArrayList<>());

            if (Simulation.artificialFailure(Double.parseDouble(config.getProperty(CONFIG_FAILURE_PROBABILITY, "0.0")))) {
                return new HotelResponse(request.getRequestID(), false, "Simulated internal failure");
            }
            if (list.size() >= hotel.getTotalRooms()) {
                return new HotelResponse(request.getRequestID(), false, "No rooms available");
            }
            if (list.contains(request.getRequestID())) {
                return new HotelResponse(request.getRequestID(), false, "Booking already exists");
            }

            list.add(request.getRequestID());
            logger.info("Booked {} at hotel {} block {}", request.getRequestID(),
                    hotel.getId(), booking.getTimeBlock());
        }
        return new HotelResponse(request.getRequestID(), true, null);
    }

    private HotelResponse tryCancel(HotelRequest request) {
        HotelBooking booking = request.getBooking();
        synchronized (this) {
            List<UUID> list = bookings.get(booking.getTimeBlock());
            if (list == null) {
                return new HotelResponse(request.getRequestID(), false, "No time block to cancel");
            }
            if (!list.contains(request.getRequestID())) {
                return new HotelResponse(request.getRequestID(), false, "No booking to cancel");
            }
            list.remove(request.getRequestID());
            logger.info("Cancelled {} at hotel {} block {}", request.getRequestID(),
                    hotel.getId(), booking.getTimeBlock());

            return new HotelResponse(request.getRequestID(), true, null);
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
        backend.close();
    }
}
