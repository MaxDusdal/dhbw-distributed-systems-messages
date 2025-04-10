package com.travelbroker.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a room in a hotel
 */
public class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private UUID roomId;
    private UUID hotelId;
    
    
    public Room(UUID hotelId) {
        if (hotelId == null) {
            throw new IllegalArgumentException("Hotel ID cannot be null");
        }
        this.roomId = UUID.randomUUID();
        this.hotelId = hotelId;
    }
    
    public UUID getRoomId() {
        return roomId;
    }
    
    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }
    
    public UUID getHotelId() {
        return hotelId;
    }
    
    public void setHotelId(UUID hotelId) {
        this.hotelId = hotelId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return Objects.equals(roomId, room.roomId);
    }
    
    @Override
    public String toString() {
        return "Room{" +
                "roomId='" + roomId + '\'' +
                ", hotelId='" + hotelId + '\'' +
                '}';
    }
} 