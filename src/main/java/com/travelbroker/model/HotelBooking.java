package com.travelbroker.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a booking for a specific hotel and time block
 */
public class HotelBooking implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID bookingId;
    private String hotelId;
    private int timeBlock;
    private boolean confirmed;

    /**
     * Creates a new hotel booking
     * 
     * @param bookingId Unique identifier for this booking
     * @param hotelId   Identifier of the hotel (e.g., "H1", "H2")
     * @param timeBlock Time block for the booking (week number, 1-100)
     */
    public HotelBooking(UUID bookingId, String hotelId, int timeBlock) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID cannot be null");
        }
        if (hotelId == null || hotelId.isEmpty()) {
            throw new IllegalArgumentException("Hotel ID cannot be null or empty");
        }
        if (timeBlock < 1 || timeBlock > 100) {
            throw new IllegalArgumentException("Time block must be between 1 and 100");
        }

        this.bookingId = bookingId;
        this.hotelId = hotelId;
        this.timeBlock = timeBlock;
        this.confirmed = false;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getHotelId() {
        return hotelId;
    }

    public void setHotelId(String hotelId) {
        this.hotelId = hotelId;
    }

    public int getTimeBlock() {
        return timeBlock;
    }

    public void setTimeBlock(int timeBlock) {
        this.timeBlock = timeBlock;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

}