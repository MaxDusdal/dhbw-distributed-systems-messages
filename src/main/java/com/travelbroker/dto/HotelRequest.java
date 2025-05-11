package com.travelbroker.dto;

import com.travelbroker.model.HotelBooking;
import java.util.UUID;
import com.travelbroker.model.HotelAction;

public class HotelRequest {
    private HotelBooking booking;
    private HotelAction action;
    public boolean answered;
    public boolean successful;
    private UUID requestID;

    // Required for Gson deserialization
    public HotelRequest() {
    }

    public HotelRequest(HotelBooking booking, HotelAction action) {
        this.booking = booking;
        this.action = action;
        this.requestID = UUID.randomUUID();
        this.answered = false;
    }

    public HotelBooking getBooking() {
        return booking;
    }

    public HotelAction getAction() {
        return action;
    }

    public void setAction(HotelAction action) {
        this.action = action;
    }

    public UUID getRequestID() {
        return requestID;
    }
}
