package com.travelbroker.dto;

import java.util.UUID;

/**  Success / failure sent back from HotelServer to the Broker. */
public class HotelResponse {
    private UUID bookingId;
    private boolean success;
    private String error;       // null when success == true

    public HotelResponse() { }   // for Gson

    public HotelResponse(UUID bookingId, boolean success, String error) {
        this.bookingId = bookingId;
        this.success   = success;
        this.error     = error;
    }

    public UUID    getBookingId() { return bookingId; }
    public boolean isSuccess()    { return success;   }
    public String  getError()     { return error;     }

    public void setBookingId(UUID id)     { this.bookingId = id; }
    public void setSuccess(boolean ok)    { this.success   = ok; }
    public void setError(String error)    { this.error     = error; }
}
