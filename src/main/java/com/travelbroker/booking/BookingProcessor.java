package com.travelbroker.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.HotelBooking;
import java.util.UUID;

/**
 * Processes booking messages and forwards them to the HotelServer.
 * Handles both booking and cancellation requests.
 */
public class BookingProcessor {
    private final HotelServer hotelServer;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new BookingProcessor for the specified HotelServer.
     * @param hotelServer The HotelServer to process bookings for
     */
    public BookingProcessor(HotelServer hotelServer) {
        if (hotelServer == null) {
            throw new IllegalArgumentException("HotelServer cannot be null");
        }
        this.hotelServer = hotelServer;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process a booking request message.
     * 
     * @param jsonMessage The JSON message containing booking details
     * @return A JSON response indicating success or failure
     */
    public String processBookingMessage(String jsonMessage) {
        try {
            // Parse the JSON message into a BookingRequest
            BookingRequest request = objectMapper.readValue(jsonMessage, BookingRequest.class);
            
            // Validate the request
            if (!isValidRequest(request)) {
                return createErrorResponse("Invalid booking request");
            }

            // Create a HotelBooking object
            HotelBooking booking = new HotelBooking(
                UUID.fromString(request.getBookingId()),
                request.getHotelId(),
                request.getTimeBlock()
            );

            // Process the booking based on action type
            boolean success;
            String message;
            
            if ("BOOK".equals(request.getAction())) {
                success = hotelServer.processBooking(booking);
                message = success ? "Booking successful" : "Time block not available";
            } else if ("CANCEL".equals(request.getAction())) {
                success = hotelServer.cancelBooking(booking);
                message = success ? "Cancellation successful" : "No booking found to cancel";
            } else {
                return createErrorResponse("Invalid action: " + request.getAction());
            }
            
            // Create and return the response
            return createResponse(success, message);
            
        } catch (Exception e) {
            return createErrorResponse("Error processing booking: " + e.getMessage());
        }
    }

    /**
     * Validates a booking request.
     * @param request The request to validate
     * @return true if the request is valid, false otherwise
     */
    private boolean isValidRequest(BookingRequest request) {
        return request != null &&
               request.getBookingId() != null && 
               !request.getBookingId().isEmpty() &&
               request.getHotelId() != null && 
               !request.getHotelId().isEmpty() &&
               request.getTimeBlock() >= 1 && 
               request.getTimeBlock() <= 100 &&
               request.getAction() != null &&
               (request.getAction().equals("BOOK") || request.getAction().equals("CANCEL"));
    }

    /**
     * Creates a success response.
     * @param success Whether the operation was successful
     * @param message The response message
     * @return JSON response string
     */
    private String createResponse(boolean success, String message) {
        try {
            BookingResponse response = new BookingResponse(success, message);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return createErrorResponse("Error creating response");
        }
    }

    /**
     * Creates an error response.
     * @param errorMessage The error message
     * @return JSON response string
     */
    private String createErrorResponse(String errorMessage) {
        try {
            BookingResponse response = new BookingResponse(false, errorMessage);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"Failed to create error response\"}";
        }
    }

    /**
     * Inner class for request deserialization
     */
    private static class BookingRequest {
        private String bookingId;    // Unique identifier for the booking
        private String hotelId;      // ID of the hotel to book
        private int timeBlock;       // Time block to book (1-100)
        private String action;       // Action type (BOOK or CANCEL)

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
     * Inner class for response serialization
     */
    private static class BookingResponse {
        private boolean success;     // Whether the operation was successful
        private String message;      // Description of the result

        public BookingResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        // Getters and setters for JSON serialization
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
} 