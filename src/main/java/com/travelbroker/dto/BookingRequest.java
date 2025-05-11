package com.travelbroker.dto;

import java.util.UUID;

/**
 * Data transfer object for booking requests.
 * This is used for JSON serialization and deserialization.
 */
public class BookingRequest {
    private UUID bookingId;
    private UUID customerId;
    private String status;
    private HotelRequest[] hotelRequests;

    // Required for Gson deserialization
    public BookingRequest() {
    }

    public BookingRequest(UUID bookingId, UUID customerId, String status, HotelRequest[] hotelRequests) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.hotelRequests = hotelRequests;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public HotelRequest[] getHotelRequests() {
        return hotelRequests;
    }

    public void setHotelRequests(HotelRequest[] hotelRequests) {
        this.hotelRequests = hotelRequests;
    }


}