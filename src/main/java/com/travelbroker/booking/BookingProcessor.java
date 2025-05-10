package com.travelbroker.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.BookingMessage;

/**
 * Processes booking messages in JSON format and forwards them to the HotelServer.
 */
public class BookingProcessor {
    private final HotelServer hotelServer;
    private final ObjectMapper objectMapper;

    public BookingProcessor(HotelServer hotelServer) {
        this.hotelServer = hotelServer;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process a JSON booking message.
     * 
     * @param jsonMessage The JSON message to process
     * @return A JSON response indicating success or failure
     */
    public String processBookingMessage(String jsonMessage) {
        try {
            // Parse the JSON message
            BookingMessage bookingMessage = objectMapper.readValue(jsonMessage, BookingMessage.class);
            
            // Validate the message
            if (!isValidMessage(bookingMessage)) {
                return createErrorResponse("Invalid booking message");
            }

            // Process the booking
            boolean success = hotelServer.processBooking(bookingMessage.getTimeBlock());
            
            // Create and return the response
            return createResponse(bookingMessage.getBookingId(), success);
            
        } catch (Exception e) {
            return createErrorResponse("Error processing booking: " + e.getMessage());
        }
    }

    private boolean isValidMessage(BookingMessage message) {
        return message.getBookingId() != null && 
               !message.getBookingId().isEmpty() &&
               message.getTimeBlock() >= 1 && 
               message.getTimeBlock() <= 100 &&
               message.getHotelId() != null && 
               !message.getHotelId().isEmpty();
    }

    private String createResponse(String bookingId, boolean success) {
        try {
            return objectMapper.writeValueAsString(new BookingResponse(bookingId, success));
        } catch (Exception e) {
            return createErrorResponse("Error creating response");
        }
    }

    private String createErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(new BookingResponse(null, false, errorMessage));
        } catch (Exception e) {
            return "{\"error\":\"Failed to create error response\"}";
        }
    }

    /**
     * Internal class for JSON response structure
     */
    private static class BookingResponse {
        private final String bookingId;
        private final boolean success;
        private final String errorMessage;

        public BookingResponse(String bookingId, boolean success) {
            this(bookingId, success, null);
        }

        public BookingResponse(String bookingId, boolean success, String errorMessage) {
            this.bookingId = bookingId;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getBookingId() {
            return bookingId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
} 