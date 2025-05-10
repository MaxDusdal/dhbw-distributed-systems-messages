package com.travelbroker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a booking request message in JSON format.
 * This class will be used to deserialize incoming JSON messages.
 */
public class BookingMessage {
    @JsonProperty("bookingId")
    private String bookingId;

    @JsonProperty("timeBlock")
    private int timeBlock;

    @JsonProperty("hotelId")
    private String hotelId;

    // Default constructor for JSON deserialization
    public BookingMessage() {}

    public BookingMessage(String bookingId, int timeBlock, String hotelId) {
        this.bookingId = bookingId;
        this.timeBlock = timeBlock;
        this.hotelId = hotelId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public int getTimeBlock() {
        return timeBlock;
    }

    public String getHotelId() {
        return hotelId;
    }

    @Override
    public String toString() {
        return "BookingMessage{" +
                "bookingId='" + bookingId + '\'' +
                ", timeBlock=" + timeBlock +
                ", hotelId='" + hotelId + '\'' +
                '}';
    }
} 