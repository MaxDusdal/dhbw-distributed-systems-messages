package com.travelbroker.model;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Represents a hotel with its properties and booking management.
 * Uses thread-safe collections for concurrent booking operations.
 */
public class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Unique identifier for the hotel
    private final String hotelId;
    // Name of the hotel
    private final String name;
    // Total number of rooms in the hotel
    private final int totalRooms;
    // Thread-safe map of time blocks to their booking status
    // Key: time block (1-100), Value: atomic boolean indicating if booked
    private final ConcurrentHashMap<Integer, AtomicBoolean> bookings;
    
    /**
     * Creates a new hotel with the specified properties.
     * @param hotelId Unique identifier for the hotel
     * @param name Name of the hotel
     * @param totalRooms Total number of rooms in the hotel
     */
    public Hotel(String hotelId, String name, int totalRooms) {
        if (hotelId == null || hotelId.isEmpty()) {
            throw new IllegalArgumentException("Hotel ID cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hotel name cannot be null or empty");
        }
        if (totalRooms <= 0) {
            throw new IllegalArgumentException("Total rooms must be positive");
        }
        
        this.hotelId = hotelId;
        this.name = name;
        this.totalRooms = totalRooms;
        this.bookings = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets the hotel's unique identifier.
     */
    public String getHotelId() {
        return hotelId;
    }
    
    /**
     * Gets the hotel's name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the total number of rooms in the hotel.
     */
    public int getTotalRooms() {
        return totalRooms;
    }

    /**
     * Gets the thread-safe map of bookings.
     * @return Map of time blocks to their booking status
     */
    public ConcurrentHashMap<Integer, AtomicBoolean> getBookings() {
        return bookings;
    }

    /**
     * Checks if a specific time block is booked.
     * @param timeBlock The time block to check (1-100)
     * @return true if the time block is booked, false otherwise
     */
    public boolean isTimeBlockBooked(int timeBlock) {
        if (timeBlock < 1 || timeBlock > 100) {
            throw new IllegalArgumentException("Time block must be between 1 and 100");
        }
        AtomicBoolean isBooked = bookings.get(timeBlock);
        return isBooked != null && isBooked.get();
    }
}