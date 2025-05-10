package com.travelbroker.model;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Represents a hotel with a fixed number of rooms and its booking state.
 * Uses a thread-safe map to track booked time blocks.
 */
public class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String hotelId;
    private final String name;
    private final int totalRooms;
    
    // Maps time blocks (1-100) to their booking status
    private final ConcurrentHashMap<Integer, AtomicBoolean> bookings = new ConcurrentHashMap<>();
    
    /**
     * Creates a new hotel
     * 
     * @param hotelId Unique identifier for the hotel (e.g., "H1", "H2")
     * @param name Name of the hotel
     * @param totalRooms Total number of rooms available
     */
    public Hotel(String hotelId, String name, int totalRooms) {
        if (hotelId == null || hotelId.isEmpty()) {
            throw new IllegalArgumentException("Hotel ID cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hotel name cannot be null or empty");
        }
        if (totalRooms <= 0) {
            throw new IllegalArgumentException("Total rooms must be greater than 0");
        }
        
        this.hotelId = hotelId;
        this.name = name;
        this.totalRooms = totalRooms;
    }
    
    public String getHotelId() {
        return hotelId;
    }
    
    public String getName() {
        return name;
    }
    
    public int getTotalRooms() {
        return totalRooms;
    }

    /**
     * Gets the booking status for a specific time block.
     * @param timeBlock The time block to check (1-100)
     * @return true if the time block is booked, false otherwise
     */
    public boolean isTimeBlockBooked(int timeBlock) {
        AtomicBoolean isBooked = bookings.get(timeBlock);
        return isBooked != null && isBooked.get();
    }

    /**
     * Gets the thread-safe map of bookings.
     * @return The ConcurrentHashMap of bookings
     */
    public ConcurrentHashMap<Integer, AtomicBoolean> getBookings() {
        return bookings;
    }
}