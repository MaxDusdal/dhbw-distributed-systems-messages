package com.travelbroker.booking;

import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client system that generates booking requests and communicates with HotelServer.
 * Uses ZeroMQ for asynchronous communication.
 */
public class BookingService implements AutoCloseable {
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    
    private final int instanceId;
    private final HotelServer hotelServer;
    private final ZContext context;
    private final ZMQ.Socket socket;
    private final ObjectMapper objectMapper;
    private boolean running;

    /**
     * Creates a new BookingService for the specified HotelServer.
     * @param hotelServer The HotelServer to communicate with
     */
    public BookingService(HotelServer hotelServer) {
        if (hotelServer == null) {
            throw new IllegalArgumentException("HotelServer cannot be null");
        }
        this.instanceId = instanceCounter.incrementAndGet();
        this.hotelServer = hotelServer;
        this.context = new ZContext();
        this.socket = context.createSocket(SocketType.REQ);
        this.objectMapper = new ObjectMapper();
        this.running = false;
    }

    /**
     * Starts the service and connects to the HotelServer.
     * @param connectAddress The ZeroMQ address to connect to (e.g., "tcp://localhost:5555")
     */
    public void start(String connectAddress) {
        if (running) {
            return;
        }

        try {
            socket.connect(connectAddress);
            running = true;
            System.out.println("BookingService #" + instanceId + " started and connected to " + connectAddress);
        } catch (Exception e) {
            System.err.println("Failed to start BookingService: " + e.getMessage());
            throw new RuntimeException("Failed to start BookingService", e);
        }
    }

    /**
     * Stops the service and releases resources.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        socket.close();
        context.close();
        System.out.println("BookingService #" + instanceId + " stopped");
    }

    /**
     * Sends a booking request to the HotelServer.
     * @param hotelId The ID of the hotel to book
     * @param timeBlock The time block to book (1-100)
     * @return true if the booking was successful, false otherwise
     */
    public boolean sendBookingRequest(String hotelId, int timeBlock) {
        if (!running) {
            throw new IllegalStateException("BookingService is not running");
        }

        try {
            // Create the booking request
            BookingRequest request = new BookingRequest(
                UUID.randomUUID().toString(),
                hotelId,
                timeBlock,
                "BOOK"
            );

            // Convert to JSON
            String requestJson = objectMapper.writeValueAsString(request);

            // Send the request
            socket.send(requestJson);

            // Wait for response
            String responseJson = socket.recvStr();
            BookingResponse response = objectMapper.readValue(responseJson, BookingResponse.class);

            return response.isSuccess();
        } catch (Exception e) {
            System.err.println("Error sending booking request: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a cancellation request to the HotelServer.
     * @param hotelId The ID of the hotel to cancel
     * @param timeBlock The time block to cancel (1-100)
     * @return true if the cancellation was successful, false otherwise
     */
    public boolean sendCancellationRequest(String hotelId, int timeBlock) {
        if (!running) {
            throw new IllegalStateException("BookingService is not running");
        }

        try {
            // Create the cancellation request
            BookingRequest request = new BookingRequest(
                UUID.randomUUID().toString(),
                hotelId,
                timeBlock,
                "CANCEL"
            );

            // Convert to JSON
            String requestJson = objectMapper.writeValueAsString(request);

            // Send the request
            socket.send(requestJson);

            // Wait for response
            String responseJson = socket.recvStr();
            BookingResponse response = objectMapper.readValue(responseJson, BookingResponse.class);

            return response.isSuccess();
        } catch (Exception e) {
            System.err.println("Error sending cancellation request: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Inner class for request serialization
     */
    private static class BookingRequest {
        private String bookingId;    // Unique identifier for the booking
        private String hotelId;      // ID of the hotel to book
        private int timeBlock;       // Time block to book (1-100)
        private String action;       // Action type (BOOK or CANCEL)

        public BookingRequest(String bookingId, String hotelId, int timeBlock, String action) {
            this.bookingId = bookingId;
            this.hotelId = hotelId;
            this.timeBlock = timeBlock;
            this.action = action;
        }

        // Getters and setters for JSON serialization
        public String getBookingId() { return bookingId; }
        public void setBookingId(String bookingId) { this.bookingId = bookingId; }
        public String getHotelId() { return hotelId; }
        public void setHotelId(String hotelId) { this.hotelId = hotelId; }
        public int getTimeBlock() { return timeBlock; }
        public void setTimeBlock(int timeBlock) { this.timeBlock = timeBlock; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    /**
     * Inner class for response deserialization
     */
    private static class BookingResponse {
        private boolean success;     // Whether the operation was successful
        private String message;      // Description of the result

        // Getters and setters for JSON serialization
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}