package com.travelbroker.model;

/**
 * Enum representing the possible states of a booking
 * 
 * State Diagram:
 * [*] --> PENDING : Booking received
 * PENDING --> PROCESSING : Begin coordination
 * PROCESSING --> CONFIRMED : All hotel bookings successful
 * PROCESSING --> ROLLING_BACK : Booking failed
 * ROLLING_BACK --> ROLLBACK_SUCCESS : All compensations succeeded
 * ROLLING_BACK --> ROLLBACK_TIMEOUT : Some compensations failed or timed out
 * ROLLBACK_SUCCESS --> FAILED : Rollback completed
 * ROLLBACK_TIMEOUT --> FAILED : Partial rollback
 * CONFIRMED --> [*] : Booking complete
 * FAILED --> [*] : Terminal failure state
 */
public enum BookingStatus {
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    CONFIRMED("CONFIRMED"),
    ROLLING_BACK("ROLLING_BACK"),
    ROLLBACK_SUCCESS("ROLLBACK_SUCCESS"),
    ROLLBACK_TIMEOUT("ROLLBACK_TIMEOUT"),
    FAILED("FAILED");
    
    private final String status;
    
    BookingStatus(String status) {
        this.status = status;
    }
    
    public String getStatus() {
        return status;
    }
    
    public static BookingStatus fromString(String text) {
        for (BookingStatus b : BookingStatus.values()) {
            if (b.status.equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("No enum constant " + text);
    }
} 