package com.travelbroker.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a trip booking that consists of multiple hotel bookings
 */
public class TripBooking implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID bookingId;
    private HotelBooking[] hotelBookings;
    private UUID customerId;
    private BookingStatus status;
    private String statusMessage;

    public TripBooking(HotelBooking[] hotelBookings, UUID customerId) {
        if (hotelBookings == null || hotelBookings.length == 0) {
            throw new IllegalArgumentException("Hotel bookings cannot be null or empty");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (hotelBookings.length > 5) {
            throw new IllegalArgumentException("Trip booking cannot have more than 5 hotel bookings");
        }
        this.bookingId = UUID.randomUUID();
        this.hotelBookings = hotelBookings;
        this.customerId = customerId;
        this.status = BookingStatus.PENDING;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public HotelBooking[] getHotelBookings() {
        return hotelBookings;
    }

    public void setHotelBookings(HotelBooking[] hotelBookings) {
        this.hotelBookings = hotelBookings;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
}