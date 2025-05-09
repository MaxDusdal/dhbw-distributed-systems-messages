package com.travelbroker.model;

import java.io.Serializable;
/**
 * Represents a hotel with a fixed number of rooms
 */
public class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private final String name;
    private final int totalRooms;
    
    /**
     * Creates a new hotel
     * 
     * @param id Unique identifier for the hotel (e.g., "H1", "H2")
     * @param name Name of the hotel
     * @param totalRooms Total number of rooms available
     */
    public Hotel(String id, String name, int totalRooms) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Hotel ID cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hotel name cannot be null or empty");
        }
        if (totalRooms <= 0) {
            throw new IllegalArgumentException("Total rooms must be greater than 0");
        }
        
        this.id = id;
        this.name = name;
        this.totalRooms = totalRooms;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public int getTotalRooms() {
        return totalRooms;
    }
}