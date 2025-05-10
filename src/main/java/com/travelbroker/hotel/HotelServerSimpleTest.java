package com.travelbroker.hotel;

import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import java.util.UUID;

public class HotelServerSimpleTest {
    public static void main(String[] args) {
        // Create a hotel
        Hotel hotel = new Hotel("HOTEL1", "Test Hotel", 5);
        HotelServer server = new HotelServer(hotel);

        System.out.println("=== Starting Simple HotelServer Test ===");
        
        // Test 1: Basic Booking
        System.out.println("\nTest 1: Basic Booking");
        HotelBooking booking1 = new HotelBooking(UUID.randomUUID(), "HOTEL1", 1);
        boolean result1 = server.processBooking(booking1);
        System.out.println("Booking for time block 1: " + (result1 ? "SUCCESS" : "FAILED"));
        System.out.println("Time block 1 is now " + (server.isTimeBlockBooked(1) ? "BOOKED" : "AVAILABLE"));

        // Test 2: Double Booking
        System.out.println("\nTest 2: Double Booking");
        HotelBooking booking2 = new HotelBooking(UUID.randomUUID(), "HOTEL1", 1);
        boolean result2 = server.processBooking(booking2);
        System.out.println("Second booking for time block 1: " + (result2 ? "SUCCESS" : "FAILED"));
        System.out.println("Time block 1 is still " + (server.isTimeBlockBooked(1) ? "BOOKED" : "AVAILABLE"));

        // Test 3: Cancel Booking
        System.out.println("\nTest 3: Cancel Booking");
        boolean cancelResult = server.cancelBooking(booking1);
        System.out.println("Cancelling booking: " + (cancelResult ? "SUCCESS" : "FAILED"));
        System.out.println("Time block 1 is now " + (server.isTimeBlockBooked(1) ? "BOOKED" : "AVAILABLE"));

        // Test 4: Book After Cancel
        System.out.println("\nTest 4: Book After Cancel");
        HotelBooking booking3 = new HotelBooking(UUID.randomUUID(), "HOTEL1", 1);
        boolean result3 = server.processBooking(booking3);
        System.out.println("New booking for time block 1: " + (result3 ? "SUCCESS" : "FAILED"));
        System.out.println("Time block 1 is now " + (server.isTimeBlockBooked(1) ? "BOOKED" : "AVAILABLE"));

        // Test 5: Invalid Time Block
        System.out.println("\nTest 5: Invalid Time Block");
        HotelBooking booking4 = new HotelBooking(UUID.randomUUID(), "HOTEL1", 101);
        boolean result4 = server.processBooking(booking4);
        System.out.println("Booking for invalid time block 101: " + (result4 ? "SUCCESS" : "FAILED"));

        // Test 6: Wrong Hotel ID
        System.out.println("\nTest 6: Wrong Hotel ID");
        HotelBooking booking5 = new HotelBooking(UUID.randomUUID(), "WRONG_HOTEL", 2);
        boolean result5 = server.processBooking(booking5);
        System.out.println("Booking with wrong hotel ID: " + (result5 ? "SUCCESS" : "FAILED"));

        // Print Summary
        System.out.println("\n=== Test Summary ===");
        System.out.println("Total tests: 6");
        System.out.println("Expected successful bookings: 2 (Test 1 and 4)");
        System.out.println("Expected failed bookings: 4 (Tests 2, 3, 5, and 6)");
    }
} 