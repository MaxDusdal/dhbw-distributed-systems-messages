package com.travelbroker.dto;

import java.util.UUID;

/** Success / failure sent back from HotelServer to the Broker. */
public class HotelResponse {
    private UUID requestId;
    private boolean success;
    private String error; // null when success == true

    public HotelResponse() {
    } // for Gson

    public HotelResponse(UUID requestId, boolean success, String error) {
        this.requestId = requestId;
        this.success = success;
        this.error = error;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public void setRequestId(UUID id) {
        this.requestId = id;
    }

    public void setSuccess(boolean ok) {
        this.success = ok;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        return "HotelResponse{" +
                "requestId=" + requestId +
                ", success=" + success +
                ", error='" + error + '\'' +
                '}';
    }
}
