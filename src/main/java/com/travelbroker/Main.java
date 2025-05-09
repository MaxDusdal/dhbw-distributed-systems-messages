package com.travelbroker;

import com.travelbroker.booking.BookingService;
import com.travelbroker.broker.TravelBroker;
import com.travelbroker.model.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * Main entry point for the application.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_BOOKING_SERVICE_COUNT = 3;
    private static final int DEFAULT_HOTEL_COUNT = 5;
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public static void main(String[] args) {
        logger.info("Starting Travel Broker Distributed System");
        
        // Number of booking service instances
        int bookingServiceCount = DEFAULT_BOOKING_SERVICE_COUNT;
        if (args.length > 0) {
            try {
                bookingServiceCount = Integer.parseInt(args[0]);
                if (bookingServiceCount < 1) {
                    logger.warn("Invalid booking service count {}, using default of {}", 
                            bookingServiceCount, DEFAULT_BOOKING_SERVICE_COUNT);
                    bookingServiceCount = DEFAULT_BOOKING_SERVICE_COUNT;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid booking service count argument, using default of {}", 
                        DEFAULT_BOOKING_SERVICE_COUNT);
            }
        }
        logger.info("Creating {} booking service instances", bookingServiceCount);
        
        // Create hotels
        List<Hotel> hotels = createHotels(DEFAULT_HOTEL_COUNT);
        logger.info("Created {} hotels", hotels.size());
        
        // Create and start the TravelBroker (singleton)
        try (TravelBroker travelBroker = new TravelBroker()) {
            travelBroker.start();
            
            // Create and start the BookingService instances
            List<BookingService> bookingServices = new ArrayList<>();
            try {
                for (int i = 0; i < bookingServiceCount; i++) {
                    BookingService bookingService = new BookingService(travelBroker, bookingServiceCount, hotels);
                    bookingServices.add(bookingService);
                    bookingService.start();
                }
                
                // Register shutdown hook to gracefully close resources
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("Shutdown initiated");
                    shutdownLatch.countDown();
                }));
                
                // Wait for user input to exit
                logger.info("System running. Press Enter to exit.");
                waitForUserInput();
                
            } finally {
                // Close all booking services
                for (BookingService bookingService : bookingServices) {
                    try {
                        bookingService.close();
                    } catch (Exception e) {
                        logger.error("Error closing booking service: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in main thread: {}", e.getMessage(), e);
        }
        
        logger.info("System shutdown complete");
    }
    
    /**
     * Creates a list of hotels.
     *
     * @param count The number of hotels to create
     * @return A list of hotels
     */
    private static List<Hotel> createHotels(int count) {
        List<Hotel> hotels = new ArrayList<>();
        
        // Create hotels with varying room capacities
        for (int i = 1; i <= count; i++) {
            String id = "H" + i;
            String name = "Hotel " + i;
            int totalRooms = 20 + (i * 10); // 30, 40, 50, 60, 70 rooms
            
            hotels.add(new Hotel(id, name, totalRooms));
            logger.info("Created hotel: {} ({}, {} rooms)", id, name, totalRooms);
        }
        
        return hotels;
    }
    
    /**
     * Waits for user input to exit the application.
     */
    private static void waitForUserInput() {
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            shutdownLatch.countDown();
        });
        inputThread.setDaemon(true);
        inputThread.start();
        
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}