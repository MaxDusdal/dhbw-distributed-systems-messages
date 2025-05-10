package com.travelbroker.hotel;

import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import java.util.UUID;

/**
 * Basic test class for HotelServer functionality.
 * This class tests all the basic operations of the HotelServer:
 * - Making a booking
 * - Preventing double bookings
 * - Canceling bookings
 * - Rebooking after cancellation
 * - Handling invalid inputs
 */
public class HotelServerBasicTest {
    // Test configuration constants
    private static final String TEST_HOTEL_ID = "H1";        // ID for our test hotel
    private static final String TEST_HOTEL_NAME = "Test Hotel";  // Name for our test hotel
    private static final int TEST_TOTAL_ROOMS = 10;          // Number of rooms in our test hotel
    private static final int TEST_TIME_BLOCK = 1;            // Time block we'll use for testing (week 1)

    /**
     * Main method that runs all the tests.
     * Creates a hotel server and runs through all test cases.
     */
    public static void main(String[] args) {
        System.out.println("Starting HotelServer Basic Tests...");
        
        // Step 1: Create our test hotel and server
        Hotel hotel = new Hotel(TEST_HOTEL_ID, TEST_HOTEL_NAME, TEST_TOTAL_ROOMS);
        HotelServer server = new HotelServer(hotel, "tcp://localhost:5555");
        
        // Step 2: Start the server in a separate thread
        // This is needed because the server needs to run continuously to handle requests
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
            }
        });
        serverThread.start();

        try {
            // Step 3: Wait a bit for the server to start up
            Thread.sleep(1000);

            // Step 4: Run all our test cases
            // Test 1: Try to make a basic booking
            System.out.println("\nTest 1: Basic Booking");
            boolean bookingSuccess = testBasicBooking(server);
            System.out.println("Basic booking test " + (bookingSuccess ? "PASSED" : "FAILED"));

            // Test 2: Try to book the same time block again (should fail)
            System.out.println("\nTest 2: Double Booking");
            boolean doubleBookingSuccess = testDoubleBooking(server);
            System.out.println("Double booking test " + (doubleBookingSuccess ? "PASSED" : "FAILED"));

            // Test 3: Try to cancel the booking
            System.out.println("\nTest 3: Cancellation");
            boolean cancellationSuccess = testCancellation(server);
            System.out.println("Cancellation test " + (cancellationSuccess ? "PASSED" : "FAILED"));

            // Test 4: Try to book again after cancellation (should work)
            System.out.println("\nTest 4: Booking After Cancellation");
            boolean rebookingSuccess = testBookingAfterCancellation(server);
            System.out.println("Booking after cancellation test " + (rebookingSuccess ? "PASSED" : "FAILED"));

            // Test 5: Try to book an invalid time block (should fail)
            System.out.println("\nTest 5: Invalid Time Block");
            boolean invalidTimeBlockSuccess = testInvalidTimeBlock(server);
            System.out.println("Invalid time block test " + (invalidTimeBlockSuccess ? "PASSED" : "FAILED"));

            // Test 6: Try to book with wrong hotel ID (should fail)
            System.out.println("\nTest 6: Wrong Hotel ID");
            boolean wrongHotelIdSuccess = testWrongHotelId(server);
            System.out.println("Wrong hotel ID test " + (wrongHotelIdSuccess ? "PASSED" : "FAILED"));

        } catch (Exception e) {
            System.err.println("Test error: " + e.getMessage());
        } finally {
            // Step 5: Clean up - stop the server and wait for it to finish
            server.stop();
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }
    }

    /**
     * Test 1: Basic Booking
     * Tries to make a simple booking for our test hotel and time block.
     * This should succeed if the server is working correctly.
     */
    private static boolean testBasicBooking(HotelServer server) {
        try {
            // Create a new booking with a random ID
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), TEST_HOTEL_ID, TEST_TIME_BLOCK);
            boolean success = server.processBooking(booking);
            System.out.println("Attempted to book time block " + TEST_TIME_BLOCK + 
                             " in hotel " + TEST_HOTEL_ID + 
                             ": " + (success ? "SUCCESS" : "FAILED"));
            return success;
        } catch (Exception e) {
            System.err.println("Basic booking test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test 2: Double Booking
     * Tries to book the same time block again.
     * This should fail because the time block is already booked.
     */
    private static boolean testDoubleBooking(HotelServer server) {
        try {
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), TEST_HOTEL_ID, TEST_TIME_BLOCK);
            boolean success = server.processBooking(booking);
            System.out.println("Attempted to double book time block " + TEST_TIME_BLOCK + 
                             " in hotel " + TEST_HOTEL_ID + 
                             ": " + (success ? "SUCCESS (unexpected)" : "FAILED (expected)"));
            return !success; // Test passes if booking fails (as expected)
        } catch (Exception e) {
            System.err.println("Double booking test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test 3: Cancellation
     * Tries to cancel the booking we made.
     * This should succeed if the booking exists.
     */
    private static boolean testCancellation(HotelServer server) {
        try {
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), TEST_HOTEL_ID, TEST_TIME_BLOCK);
            boolean success = server.cancelBooking(booking);
            System.out.println("Attempted to cancel booking for time block " + TEST_TIME_BLOCK + 
                             " in hotel " + TEST_HOTEL_ID + 
                             ": " + (success ? "SUCCESS" : "FAILED"));
            return success;
        } catch (Exception e) {
            System.err.println("Cancellation test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test 4: Booking After Cancellation
     * Tries to book the same time block after we cancelled it.
     * This should succeed because the time block is now free.
     */
    private static boolean testBookingAfterCancellation(HotelServer server) {
        try {
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), TEST_HOTEL_ID, TEST_TIME_BLOCK);
            boolean success = server.processBooking(booking);
            System.out.println("Attempted to book time block " + TEST_TIME_BLOCK + 
                             " in hotel " + TEST_HOTEL_ID + 
                             " after cancellation: " + (success ? "SUCCESS" : "FAILED"));
            return success;
        } catch (Exception e) {
            System.err.println("Booking after cancellation test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test 5: Invalid Time Block
     * Tries to book a time block that's outside the valid range (1-100).
     * This should fail because the time block is invalid.
     */
    private static boolean testInvalidTimeBlock(HotelServer server) {
        try {
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), TEST_HOTEL_ID, 101); // Invalid time block
            boolean success = server.processBooking(booking);
            System.out.println("Attempted to book invalid time block 101 in hotel " + TEST_HOTEL_ID + 
                             ": " + (success ? "SUCCESS (unexpected)" : "FAILED (expected)"));
            return !success; // Test passes if booking fails (as expected)
        } catch (Exception e) {
            System.err.println("Invalid time block test error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Test 6: Wrong Hotel ID
     * Tries to book a time block for a hotel that doesn't exist.
     * This should fail because the hotel ID is wrong.
     */
    private static boolean testWrongHotelId(HotelServer server) {
        try {
            HotelBooking booking = new HotelBooking(UUID.randomUUID(), "WRONG_HOTEL", TEST_TIME_BLOCK);
            boolean success = server.processBooking(booking);
            System.out.println("Attempted to book time block " + TEST_TIME_BLOCK + 
                             " in wrong hotel WRONG_HOTEL: " + 
                             (success ? "SUCCESS (unexpected)" : "FAILED (expected)"));
            return !success; // Test passes if booking fails (as expected)
        } catch (Exception e) {
            System.err.println("Wrong hotel ID test error: " + e.getMessage());
            return false;
        }
    }
} 