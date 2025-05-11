package com.travelbroker.dto;

import com.travelbroker.model.HotelBooking;
import java.util.UUID;

public class HotelRequest {
    public enum Action {
        BOOK,
        CANCEL
    }
    private HotelBooking booking;
    private Action action;
    private boolean confirmed;
    private UUID requestID;

    // Required for Gson deserialization
    public HotelRequest() {
    }

    public HotelRequest(HotelBooking booking, Action action) {
        this.booking = booking;
        this.action = action;
        this.confirmed = false;
        this.requestID = UUID.randomUUID();
    }

    public HotelBooking getBooking() {
        return booking;
    }

    public Action getAction() {
        return action;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public UUID getRequestID() {
        return requestID;
    }
}
