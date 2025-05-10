package com.travelbroker.hotel;

import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles booking operations for a specific hotel.
 * Listens for booking requests via ZeroMQ and processes them.
 */
public class HotelServer {
    private final Hotel hotel;
    private final ZContext context;
    private final ZMQ.Socket socket;
    private final ObjectMapper objectMapper;
    private boolean running;

    public HotelServer(Hotel hotel, String bindAddress) {
        if (hotel == null) {
            throw new IllegalArgumentException("Hotel cannot be null");
        }
        this.hotel = hotel;
        this.context = new ZContext();
        this.socket = context.createSocket(SocketType.REP);
        this.socket.bind(bindAddress);
        this.objectMapper = new ObjectMapper();
        this.running = false;
    }

    /**
     * Starts the server to listen for booking requests.
     */
    public void start() {
        running = true;
        System.out.println("HotelServer for " + hotel.getHotelId() + " started and listening for requests...");
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Wait for next request from client
                String request = socket.recvStr();
                System.out.println("Received request: " + request);
                
                // Process the request and get response
                String response = processRequest(request);
                
                // Send response back to client
                socket.send(response);
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                socket.send("ERROR: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the server.
     */
    public void stop() {
        running = false;
        socket.close();
        context.close();
    }

    /**
     * Processes an incoming request and returns a response.
     */
    private String processRequest(String request) {
        try {
            // Parse the request JSON
            BookingRequest bookingRequest = objectMapper.readValue(request, BookingRequest.class);
            
            // Verify this is the correct hotel
            if (!hotel.getHotelId().equals(bookingRequest.getHotelId())) {
                return createResponse(false, "Hotel ID mismatch");
            }

            // Process the booking
            if (bookingRequest.getAction().equals("BOOK")) {
                boolean success = processBooking(new HotelBooking(
                    UUID.fromString(bookingRequest.getBookingId()),
                    bookingRequest.getHotelId(),
                    bookingRequest.getTimeBlock()
                ));
                return createResponse(success, success ? "Booking successful" : "Time block not available");
            } else if (bookingRequest.getAction().equals("CANCEL")) {
                boolean success = cancelBooking(new HotelBooking(
                    UUID.fromString(bookingRequest.getBookingId()),
                    bookingRequest.getHotelId(),
                    bookingRequest.getTimeBlock()
                ));
                return createResponse(success, success ? "Cancellation successful" : "No booking found to cancel");
            } else {
                return createResponse(false, "Invalid action: " + bookingRequest.getAction());
            }
        } catch (Exception e) {
            return createResponse(false, "Error processing request: " + e.getMessage());
        }
    }

    private String createResponse(boolean success, String message) {
        try {
            BookingResponse response = new BookingResponse(success, message);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"Error creating response: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Processes a hotel booking request.
     *
     * @param booking The booking request to process
     * @return true if the booking was successful, false if already booked
     */
    public boolean processBooking(HotelBooking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null");
        }

        // Verify this is the correct hotel
        if (!hotel.getHotelId().equals(booking.getHotelId())) {
            System.out.println("Hotel ID mismatch: " + booking.getHotelId() + " vs " + hotel.getHotelId());
            return false;
        }

        int timeBlock = booking.getTimeBlock();
        if (timeBlock < 1 || timeBlock > 100) {
            System.out.println("Invalid time block: " + timeBlock + ". Must be between 1 and 100.");
            return false;
        }

        // Get or create the booking status for this time block
        AtomicBoolean isBooked = hotel.getBookings().computeIfAbsent(timeBlock, k -> new AtomicBoolean(false));
        
        // Try to book the time slot if it's not already booked
        if (isBooked.compareAndSet(false, true)) {
            booking.setConfirmed(true);
            System.out.println("Successfully booked time block " + timeBlock + " in hotel " + hotel.getHotelId() + 
                             " for booking " + booking.getBookingId());
            return true;
        } else {
            System.out.println("Time block " + timeBlock + " is already booked in hotel " + hotel.getHotelId());
            return false;
        }
    }

    /**
     * Gets the hotel this server manages.
     */
    public Hotel getHotel() {
        return hotel;
    }

    /**
     * Cancels a booking for the specified time block.
     *
     * @param booking The booking to cancel
     * @return true if the cancellation was successful, false if not booked
     */
    public boolean cancelBooking(HotelBooking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null");
        }

        // Verify this is the correct hotel
        if (!hotel.getHotelId().equals(booking.getHotelId())) {
            System.out.println("Hotel ID mismatch: " + booking.getHotelId() + " vs " + hotel.getHotelId());
            return false;
        }

        int timeBlock = booking.getTimeBlock();
        if (timeBlock < 1 || timeBlock > 100) {
            throw new IllegalArgumentException("Time block must be between 1 and 100");
        }

        AtomicBoolean isBooked = hotel.getBookings().get(timeBlock);
        if (isBooked == null || !isBooked.get()) {
            return false;
        }

        boolean cancelled = isBooked.compareAndSet(true, false);
        if (cancelled) {
            booking.setConfirmed(false);
            System.out.println("Successfully cancelled booking " + booking.getBookingId() + 
                             " for time block " + timeBlock + " in hotel " + hotel.getHotelId());
        }
        return cancelled;
    }

    /**
     * Checks if a time block is booked.
     *
     * @param timeBlock The time block to check (1-100)
     * @return true if the time block is booked, false otherwise
     */
    public boolean isTimeBlockBooked(int timeBlock) {
        return hotel.isTimeBlockBooked(timeBlock);
    }

    /**
     * Inner class for request deserialization
     */
    private static class BookingRequest {
        private String bookingId;
        private String hotelId;
        private int timeBlock;
        private String action;

        // Getters and setters
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
     * Inner class for response serialization
     */
    private static class BookingResponse {
        private boolean success;
        private String message;

        public BookingResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
