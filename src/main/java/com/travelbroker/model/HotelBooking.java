package com.travelbroker.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a hotel booking in the system
 */
public class HotelBooking implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID hotelBookingId;
    private UUID hotelId;
    private int startTimeBlock;
    private int endTimeBlock;
    private UUID customerId;

    public HotelBooking(UUID hotelId, int startTimeBlock, int endTimeBlock, UUID customerId) {
        if (startTimeBlock < 0 || endTimeBlock < 0 || startTimeBlock >= endTimeBlock) {
            throw new IllegalArgumentException("Invalid time block values");
        }
        if (startTimeBlock > 100 || endTimeBlock > 100) {
            throw new IllegalArgumentException("Time block values must be less than 100");
        }
        this.hotelBookingId = UUID.randomUUID();
        this.hotelId = hotelId;
        this.startTimeBlock = startTimeBlock;
        this.endTimeBlock = endTimeBlock;
        this.customerId = customerId;
    }

    public UUID getHotelBookingId() {
        return hotelBookingId;
    }

    public void setHotelBookingId(UUID hotelBookingId) {
        this.hotelBookingId = hotelBookingId;
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public void setHotelId(UUID hotelId) {
        this.hotelId = hotelId;
    }

    public int getStartTimeBlock() {
        return startTimeBlock;
    }

    public void setStartTimeBlock(int startTimeBlock) {
        this.startTimeBlock = startTimeBlock;
    }

    public int getEndTimeBlock() {
        return endTimeBlock;
    }

    public void setEndTimeBlock(int endTimeBlock) {
        this.endTimeBlock = endTimeBlock;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HotelBooking that = (HotelBooking) o;
        return Objects.equals(hotelBookingId, that.hotelBookingId);
    }

    @Override
    public String toString() {
        return "HotelBooking{" +
                "hotelBookingId='" + hotelBookingId + '\'' +
                ", hotelId='" + hotelId + '\'' +
                ", startTimeBlock=" + startTimeBlock +
                ", endTimeBlock=" + endTimeBlock +
                '}';
    }
}