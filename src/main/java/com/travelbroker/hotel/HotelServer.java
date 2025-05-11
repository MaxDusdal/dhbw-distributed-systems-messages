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

// The HotelServer class represents a server for a single hotel.
public final class HotelServer implements AutoCloseable {
    // Configuration property names for simulation parameters
    private static final String CONFIG_AVERAGE_PROCESSING_TIME = "AVERAGE_PROCESSING_TIME";  // Average processing time
    private static final String CONFIG_FAILURE_PROBABILITY = "BOOKING_FAILURE_PROBABILITY";  // Probability that bookings fail
    private static final String CONFIG_LOSS_PROBABILITY = "MESSAGE_LOSS_PROBABILITY";        // Probability that messages are lost


    private static final Logger logger = LoggerFactory.getLogger(HotelServer.class);

    // The hotel that this server represents
    private final Hotel hotel;

    // ZeroMQ client for communication with the broker
    private final ZeroMQClient backend;

    // Configuration properties loaded from a configuration file
    private final Properties config;

    /**
     * time-block → list of bookingIds
     */
    private final Map<Integer, List<UUID>> bookings = new HashMap<>();

    /**
     * isolate business work from the ZeroMQ listener thread
     */
    private final ExecutorService pool;

    // Constructor that initializes the server with a specific hotel
    public HotelServer(Hotel hotel) {
        this.hotel = Objects.requireNonNull(hotel);
        this.config = ConfigProvider.loadConfiguration();

        pool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("business-", 0).factory());

        // Creates a ZeroMQ client for communication with the broker
        // DEALER socket type is used for asynchronous communication
        backend = new ZeroMQClient(TravelBroker.getBackendEndpoint(), SocketType.DEALER);
        // Connects to the broker
        backend.connect();
        // Sends a READY message to the broker with this hotel's ID
        backend.sendRequest("READY:" + hotel.getId()); // tell broker that system is idle

        // Starts listening for incoming messages and handles them with the handleMessage method
        backend.listenForResponses(this::handleMessage);
    }

    // Handles incoming messages from the broker
    private void handleMessage(String json) {
        // Submits the message handling task to the thread pool to not block the network thread
        pool.submit(() -> {
            // Parses the JSON string into a HotelRequest object
            HotelRequest request = JsonUtil.fromJson(json, HotelRequest.class);
            HotelResponse response;

            // Processes the request based on its action type (BOOK or CANCEL)
            switch (request.getAction()) {
                case BOOK -> response = tryBook(request);       // Tries to book a room
                case CANCEL -> response = tryCancel(request);   // Tries to cancel a booking
                default -> response = new HotelResponse(        // Unknown action
                        request.getRequestID(), false,
                        "Unknown action: " + request.getAction());
            }

            // Simulates message loss based on configured probability
            // This is for simulating network failures for testing
            if (Simulation.artificialFailure(Double.parseDouble(config.getProperty(CONFIG_LOSS_PROBABILITY, "0.0")))) {
                logger.info("Simulated message loss for {} of booking {}", request.getAction(), request.getRequestID());
                return; // Message is intentionally dropped, no response is sent
            }

            // Sends the response back to the broker
            backend.sendRequest(JsonUtil.toJson(response));
        });
    }

    // Tries to book a room for the given request
    private HotelResponse tryBook(HotelRequest request) {
        HotelBooking booking = request.getBooking();

        // Simulates processing delay based on configuration
        Simulation.artificialLatency(
                Integer.parseInt(config.getProperty(CONFIG_AVERAGE_PROCESSING_TIME, "0")));

        // Synchronizes to prevent concurrent modifications to the bookings map
        synchronized (this) {
            // Gets or creates the list of bookings for the requested time block
            List<UUID> list = bookings.computeIfAbsent(booking.getTimeBlock(), k -> new ArrayList<>());

            // Simulates internal booking failure based on configuration
            if (Simulation
                    .artificialFailure(Double.parseDouble(config.getProperty(CONFIG_FAILURE_PROBABILITY, "0.0")))) {
                return new HotelResponse(request.getRequestID(), false,
                        "Simulated internal failure");
            }

            // Checks if rooms are available for the requested time block
            if (list.size() >= hotel.getTotalRooms()) {
                return new HotelResponse(request.getRequestID(), false, "No rooms available");
            }

            // Checks if this booking already exists
            if (list.contains(request.getRequestID())) {
                return new HotelResponse(request.getRequestID(), false,
                        "Booking already exists");
            }

            // Adds the booking to the list for this time block
            list.add(request.getRequestID());
            logger.info("Booked {} at hotel {} block {}", request.getRequestID(),
                    hotel.getId(), booking.getTimeBlock());
        }

        // Returns a success response
        return new HotelResponse(request.getRequestID(), true, null);
    }

    // Tries to cancel a booking for the given request
    private HotelResponse tryCancel(HotelRequest request) {
        HotelBooking booking = request.getBooking();

        // Synchronizes to prevent concurrent modifications to the bookings map
        synchronized (this) {
            // Gets the list of bookings for the time block
            List<UUID> list = bookings.get(booking.getTimeBlock());

            // Simulates internal booking failure based on configuration
            if (Simulation
                    .artificialFailure(Double.parseDouble(config.getProperty(CONFIG_FAILURE_PROBABILITY, "0.0")))) {
                return new HotelResponse(request.getRequestID(), false,
                        "Simulated internal failure");
            }

            // Checks if the time block exists
            if (list == null) {
                return new HotelResponse(request.getRequestID(), false,
                        "No time block to cancel");
            }

            // Checks if the booking exists
            if (!list.contains(request.getRequestID())) {
                return new HotelResponse(request.getRequestID(), false, "No booking to cancel");
            }

            // Removes the booking from the list
            list.remove(request.getRequestID());
            logger.info("Cancelled {} at hotel {} block {}", request.getRequestID(),
                    hotel.getId(), booking.getTimeBlock());

            // Returns a success response
            return new HotelResponse(request.getRequestID(), true, null);
        }
    }

    @Override
    public void close() {
        pool.shutdownNow();
        backend.close();
    }
}
