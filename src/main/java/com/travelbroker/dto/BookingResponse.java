package com.travelbroker.dto;

import java.util.UUID;

/**
 * Data transfer object for booking responses.
 * This is used for JSON serialization and deserialization.
 */
public class BookingResponse {
    private UUID bookingId;
    private String status;
    private String message;

    // Required for Gson deserialization
    public BookingResponse() {
    }

    public BookingResponse(UUID bookingId, String status, String message) {
        this.bookingId = bookingId;
        this.status = status;
        this.message = message;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 