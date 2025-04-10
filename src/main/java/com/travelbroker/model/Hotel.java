package com.travelbroker.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a hotel in the system
 */
public class Hotel implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID hotelId;
    private String name;
    private List<Room> rooms;

    public Hotel(String name, int roomCount) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Hotel name cannot be null or empty");
        }
        if (roomCount <= 0) {
            throw new IllegalArgumentException("Room count must be greater than 0");
        }
        if (roomCount > 100) {
            throw new IllegalArgumentException("Room count must be less than 100");
        }
        this.hotelId = UUID.randomUUID();
        this.name = name;
        this.rooms = new ArrayList<>();
        generateRooms(roomCount);
    }

    private void generateRooms(int count) {
        for (int i = 1; i <= count; i++) {
            Room room = new Room(this.hotelId);
            this.rooms.add(room);
        }
    }

    public UUID getHotelId() {
        return hotelId;
    }

    public void setHotelId(UUID hotelId) {
        this.hotelId = hotelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Hotel hotel = (Hotel) o;
        return Objects.equals(hotelId, hotel.hotelId);
    }

    @Override
    public String toString() {
        return "Hotel{" +
                "hotelId='" + hotelId + '\'' +
                ", name='" + name + '\'' +
                ", roomCount='" + (rooms != null ? rooms.size() : 0) + '\'' +
                '}';
    }
}