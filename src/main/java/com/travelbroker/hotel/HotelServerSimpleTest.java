package com.travelbroker.hotel;

// Import required classes for ZeroMQ communication, JSON handling, and our model classes
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class HotelServerSimpleTest {
    public static void main(String[] args) {
        // Create a test hotel with ID "H1", name "Test Hotel", and 10 rooms
        Hotel hotel = new Hotel("H1", "Test Hotel", 10);
        
        // Create a HotelServer instance and bind it to localhost port 5555
        HotelServer server = new HotelServer(hotel, "tcp://localhost:5555");
        // Start the server in a separate thread to handle requests asynchronously
        new Thread(() -> server.start()).start();
        
        // Wait for 1 second to ensure the server is fully started
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create a ZeroMQ context and socket for client communication
        try (ZContext context = new ZContext()) {
            // Create a REQ (Request) socket for sending requests and receiving responses
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            // Connect to the server's address
            socket.connect("tcp://localhost:5555");
            // Create a JSON mapper for serializing/deserializing messages
            ObjectMapper mapper = new ObjectMapper();

            // Test 1: Try to make a basic booking
            System.out.println("\nTest 1: Basic booking");
            // Create a booking request with a random UUID, hotel H1, time block 1
            String bookingRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),  // Generate a unique booking ID
                "H1",                          // Target hotel ID
                1,                             // Time block to book
                "BOOK"                         // Action type
            ));
            // Send the request to the server
            socket.send(bookingRequest);
            // Wait for and receive the server's response
            String response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 2: Try to book the same time block again (should fail)
            System.out.println("\nTest 2: Double booking attempt");
            socket.send(bookingRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 3: Try to cancel the booking
            System.out.println("\nTest 3: Cancel booking");
            // Create a cancellation request for the same time block
            String cancelRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H1",
                1,
                "CANCEL"  // Action type is now CANCEL
            ));
            socket.send(cancelRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 4: Try to book the time block again after cancellation (should succeed)
            System.out.println("\nTest 4: Book after cancellation");
            socket.send(bookingRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 5: Try to book an invalid time block (should fail)
            System.out.println("\nTest 5: Invalid time block");
            String invalidRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H1",
                101,  // Invalid time block (must be 1-100)
                "BOOK"
            ));
            socket.send(invalidRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 6: Try to book with wrong hotel ID (should fail)
            System.out.println("\nTest 6: Wrong hotel ID");
            String wrongHotelRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H2",  // Wrong hotel ID (server is for H1)
                1,
                "BOOK"
            ));
            socket.send(wrongHotelRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);
        } catch (Exception e) {
            // Print any errors that occur during communication
            e.printStackTrace();
        } finally {
            // Always stop the server when we're done
            server.stop();
        }
    }

    // Inner class to represent the structure of booking requests
    private static class BookingRequest {
        private String bookingId;    // Unique identifier for the booking
        private String hotelId;      // ID of the hotel to book
        private int timeBlock;       // Time block to book (1-100)
        private String action;       // Action type (BOOK or CANCEL)

        // Constructor to create a new booking request
        public BookingRequest(String bookingId, String hotelId, int timeBlock, String action) {
            this.bookingId = bookingId;
            this.hotelId = hotelId;
            this.timeBlock = timeBlock;
            this.action = action;
        }

        // Getters and setters for JSON serialization/deserialization
        public String getBookingId() { return bookingId; }
        public void setBookingId(String bookingId) { this.bookingId = bookingId; }
        public String getHotelId() { return hotelId; }
        public void setHotelId(String hotelId) { this.hotelId = hotelId; }
        public int getTimeBlock() { return timeBlock; }
        public void setTimeBlock(int timeBlock) { this.timeBlock = timeBlock; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
} 