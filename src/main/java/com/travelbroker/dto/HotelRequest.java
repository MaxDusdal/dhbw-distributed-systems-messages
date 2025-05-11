package com.travelbroker.dto;

import com.travelbroker.model.HotelBooking;

public class HotelRequest {
    public enum Action {
        BOOK,
        CANCEL
    }
    private HotelBooking booking;
    private Action action;
    // TODO: add confirmed attribute?

    // Required for Gson deserialization
    public HotelRequest() {
    }

    public HotelRequest(HotelBooking booking, Action action) {
        this.booking = booking;
        this.action = action;
    }

    public HotelBooking getBooking() {
        return booking;
    }

    public Action getAction() {
        return action;
    }
}
