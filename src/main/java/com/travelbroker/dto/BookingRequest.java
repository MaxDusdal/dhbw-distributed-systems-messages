package com.travelbroker.dto;

import java.util.UUID;

/**
 * Data transfer object for booking requests.
 * This is used for JSON serialization and deserialization.
 */
public class BookingRequest {
    private UUID bookingId;
    private UUID customerId;
    private String status;
    private HotelBookingDTO[] hotelBookings;

    // Required for Gson deserialization
    public BookingRequest() {
    }

    public BookingRequest(UUID bookingId, UUID customerId, String status, HotelBookingDTO[] hotelBookings) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.hotelBookings = hotelBookings;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public HotelBookingDTO[] getHotelBookings() {
        return hotelBookings;
    }

    public void setHotelBookings(HotelBookingDTO[] hotelBookings) {
        this.hotelBookings = hotelBookings;
    }

    /**
     * Data transfer object for hotel bookings within a booking request.
     */
    public static class HotelBookingDTO {
        private UUID bookingId;
        private String hotelId;
        private int timeBlock;

        // Required for Gson deserialization
        public HotelBookingDTO() {
        }

        public HotelBookingDTO(UUID bookingId, String hotelId, int timeBlock) {
            this.bookingId = bookingId;
            this.hotelId = hotelId;
            this.timeBlock = timeBlock;
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
    }
} 