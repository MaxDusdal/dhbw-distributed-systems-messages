package com.travelbroker.hotel;

import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class HotelServerSimpleTest {
    public static void main(String[] args) {
        // Create a hotel
        Hotel hotel = new Hotel("H1", "Test Hotel", 10);
        
        // Create and start the hotel server
        HotelServer server = new HotelServer(hotel, "tcp://localhost:5555");
        new Thread(() -> server.start()).start();
        
        // Give the server time to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create a client to communicate with the server
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:5555");
            ObjectMapper mapper = new ObjectMapper();

            // Test 1: Basic booking
            System.out.println("\nTest 1: Basic booking");
            String bookingRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H1",
                1,
                "BOOK"
            ));
            socket.send(bookingRequest);
            String response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 2: Double booking attempt
            System.out.println("\nTest 2: Double booking attempt");
            socket.send(bookingRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 3: Cancel booking
            System.out.println("\nTest 3: Cancel booking");
            String cancelRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H1",
                1,
                "CANCEL"
            ));
            socket.send(cancelRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 4: Book after cancellation
            System.out.println("\nTest 4: Book after cancellation");
            socket.send(bookingRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 5: Invalid time block
            System.out.println("\nTest 5: Invalid time block");
            String invalidRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H1",
                101,  // Invalid time block
                "BOOK"
            ));
            socket.send(invalidRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);

            // Test 6: Wrong hotel ID
            System.out.println("\nTest 6: Wrong hotel ID");
            String wrongHotelRequest = mapper.writeValueAsString(new BookingRequest(
                UUID.randomUUID().toString(),
                "H2",  // Wrong hotel ID
                1,
                "BOOK"
            ));
            socket.send(wrongHotelRequest);
            response = socket.recvStr();
            System.out.println("Response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Stop the server
            server.stop();
        }
    }

    // Helper class for request serialization
    private static class BookingRequest {
        private String bookingId;
        private String hotelId;
        private int timeBlock;
        private String action;

        public BookingRequest(String bookingId, String hotelId, int timeBlock, String action) {
            this.bookingId = bookingId;
            this.hotelId = hotelId;
            this.timeBlock = timeBlock;
            this.action = action;
        }

        // Getters and setters
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