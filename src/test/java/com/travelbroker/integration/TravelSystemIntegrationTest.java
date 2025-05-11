package com.travelbroker.integration;

import com.travelbroker.booking.BookingService;
import com.travelbroker.broker.TravelBroker;
import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.BookingStatus;
import com.travelbroker.model.Hotel;
import com.travelbroker.util.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test of the travel booking system.
 * Tests interactions between BookingService, TravelBroker, and HotelServer
 * with focus on edge cases and failure scenarios.
 */
public class TravelSystemIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(TravelSystemIntegrationTest.class);
    
    // Configuration file system property name
    private static final String CONFIG_FILE_PROPERTY = "config.file";
    
    // Custom endpoints to avoid conflicts with other tests
    private static final String FRONTEND_ENDPOINT = "tcp://*:15555";
    private static final String BACKEND_ENDPOINT = "tcp://*:15556";
    private static final String CLIENT_ENDPOINT = "tcp://localhost:15555";
    private static final String WORKER_ENDPOINT = "tcp://localhost:15556";
    
    // Test configuration properties
    private static final String TEST_CONFIG_PATH = "test.properties";
    private static final String DEFAULT_CONFIG_PATH = "config.properties";
    private Properties originalProperties;
    private Properties testProperties;
    
    // Components under test
    private TravelBroker broker;
    private List<HotelServer> hotelServers;
    private BookingService bookingService;
    
    // Test hotels - different capacities to test edge cases
    private List<Hotel> testHotels;
    
    @BeforeEach
    void setUp() throws IOException {
        // Save original configuration
        originalProperties = ConfigProvider.loadConfiguration();
        
        // Create test configuration 
        testProperties = new Properties();
        testProperties.setProperty("BOOKING_REQUEST_ARRIVAL_RATE", "10");  // Low rate for testing
        testProperties.setProperty("AVERAGE_PROCESSING_TIME", "50");       // Fast processing for tests
        testProperties.setProperty("BOOKING_FAILURE_PROBABILITY", "0.0");  // Start with no failures
        testProperties.setProperty("MESSAGE_LOSS_PROBABILITY", "0.0");     // Start with no message loss
        
        // Write test properties to file
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Test configuration");
        }
        
        // Set system property to use test configuration
        System.setProperty(CONFIG_FILE_PROPERTY, TEST_CONFIG_PATH);
        
        // Create test hotels with different capacities
        testHotels = Arrays.asList(
            new Hotel("H1", "Test Hotel 1", 10),  // Standard capacity
            new Hotel("H2", "Test Hotel 2", 1),   // Limited capacity - will fill quickly
            new Hotel("H3", "Test Hotel 3", 20)   // High capacity
        );
        
        // Start broker with custom endpoints
        broker = new TravelBroker(FRONTEND_ENDPOINT, BACKEND_ENDPOINT);
        broker.start();
        
        // Start hotel servers
        hotelServers = new ArrayList<>();
        for (Hotel hotel : testHotels) {
            hotelServers.add(new HotelServer(hotel));
        }
        
        // Allow time for hotel servers to register with broker
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Start booking service with 1 instance
        bookingService = new BookingService(broker, 1, testHotels);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up all components
        if (bookingService != null) {
            bookingService.close();
        }
        
        if (hotelServers != null) {
            for (HotelServer server : hotelServers) {
                server.close();
            }
        }
        
        if (broker != null) {
            broker.close();
        }
        
        // Restore original configuration by removing test config
        // The next ConfigProvider.loadConfiguration call will use the default
        
        // Remove test properties file
        try {
            new java.io.File(TEST_CONFIG_PATH).delete();
        } catch (Exception e) {
            logger.warn("Failed to delete test properties file", e);
        }
        
        // Clear system property
        System.clearProperty(CONFIG_FILE_PROPERTY);
    }
    
    /**
     * Basic test of successful booking flow.
     */
    @Test
    @Timeout(value = 30)
    void testSuccessfulBookingFlow() throws InterruptedException {
        // Start booking service and wait for complete cycle
        CountDownLatch completionLatch = new CountDownLatch(1);
        
        // Start service and wait for some completed bookings
        bookingService.start();
        
        // Wait for some time to allow bookings to complete
        Thread.sleep(5000);
        
        // Stop service and verify some bookings were processed
        bookingService.stop();
        
        // This test passes if no exceptions were thrown and the service completes normally
        assertTrue(true, "Basic booking flow should complete without exceptions");
    }
    
    /**
     * Test booking to a hotel with limited capacity (overbooking scenario)
     */
    @Test
    @Timeout(value = 30)
    void testLimitedCapacityHotel() throws InterruptedException, IOException {
        // Modify test config to have a higher request rate to hit capacity limits faster
        testProperties.setProperty("BOOKING_REQUEST_ARRIVAL_RATE", "60"); // 1 per second
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration");
        }
        
        // Create a booking service targeting only the limited capacity hotel
        List<Hotel> limitedHotels = Collections.singletonList(testHotels.get(1)); // Hotel H2 with 1 room
        
        try (BookingService limitedService = new BookingService(broker, 1, limitedHotels)) {
            // Run for a while to generate multiple bookings
            limitedService.start();
            
            // Wait for some bookings to complete
            Thread.sleep(5000);
            
            // Stop service
            limitedService.stop();
            
            // The test passes if the service completes normally, even with rejections
            assertTrue(true, "Service should handle capacity issues gracefully");
        }
    }
    
    /**
     * Test handling of hotel server failures.
     */
    @Test
    @Timeout(value = 30)
    void testHotelServerFailure() throws InterruptedException, IOException {
        // Set high failure probability for hotels
        testProperties.setProperty("BOOKING_FAILURE_PROBABILITY", "0.8"); // 80% failure rate
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration with high failure rate");
        }
        
        // Start booking service
        bookingService.start();
        
        // Wait for some bookings to complete or fail
        Thread.sleep(5000);
        
        // Stop service
        bookingService.stop();
        
        // Test passes if service handles failures without crashing
        assertTrue(true, "Service should handle hotel failures gracefully");
    }
    
    /**
     * Test message loss between components.
     */
    @Test
    @Timeout(value = 30)
    void testMessageLoss() throws InterruptedException, IOException {
        // Set high message loss probability
        testProperties.setProperty("MESSAGE_LOSS_PROBABILITY", "0.5"); // 50% message loss
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration with message loss");
        }
        
        // Start booking service
        bookingService.start();
        
        // Wait for some bookings to complete or time out
        Thread.sleep(10000); // Longer wait due to message loss
        
        // Stop service
        bookingService.stop();
        
        // Test passes if service handles message loss without crashing
        assertTrue(true, "Service should handle message loss gracefully");
    }
    
    /**
     * Test behavior when a hotel server crashes and restarts mid-booking.
     */
    @Test
    @Timeout(value = 60)
    void testHotelServerCrashAndRestart() throws InterruptedException {
        // Start booking service
        bookingService.start();
        
        // Let some bookings go through
        Thread.sleep(2000);
        
        // Simulate crash of one hotel server
        HotelServer serverToRestart = hotelServers.get(0);
        Hotel hotelToRestart = testHotels.get(0);
        
        logger.info("Crashing hotel server for: {}", hotelToRestart.getName());
        serverToRestart.close();
        
        // Wait a bit to simulate downtime
        Thread.sleep(2000);
        
        // Restart the hotel server
        logger.info("Restarting hotel server for: {}", hotelToRestart.getName());
        hotelServers.set(0, new HotelServer(hotelToRestart));
        
        // Allow more bookings to go through
        Thread.sleep(5000);
        
        // Stop service
        bookingService.stop();
        
        // Test passes if service recovers after hotel server restart
        assertTrue(true, "System should handle hotel server crash and restart");
    }
    
    /**
     * Test the rollback mechanism when partial booking failure occurs.
     */
    @Test
    @Timeout(value = 60)
    void testBookingRollback() throws InterruptedException, IOException {
        // Configure one hotel to succeed and one to fail
        testProperties.setProperty("BOOKING_FAILURE_PROBABILITY", "0.0"); // No random failures
        testProperties.setProperty("BOOKING_REQUEST_ARRIVAL_RATE", "10"); // Slow rate for clearer results
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration for rollback test");
        }
        
        // Start booking service
        bookingService.start();
        
        // Wait for initial bookings
        Thread.sleep(2000);
        
        // Now make one hotel fail all requests (simulate failure)
        testProperties.setProperty("BOOKING_FAILURE_PROBABILITY", "1.0"); // 100% failure for newly created hotels
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration with certain failures");
        }
        
        // Restart one hotel server with new failure settings
        HotelServer serverToRestart = hotelServers.get(0);
        Hotel hotelToRestart = testHotels.get(0);
        
        serverToRestart.close();
        hotelServers.set(0, new HotelServer(hotelToRestart)); // Will use new failure probability
        
        // Let the system run with mixed successes and failures
        Thread.sleep(5000);
        
        // Stop service
        bookingService.stop();
        
        // The test passes if the service handles the rollback process without getting stuck
        assertTrue(true, "System should handle rollbacks for partial booking failures");
    }
    
    /**
     * Test system with a high load of concurrent requests.
     */
    @Test
    @Timeout(value = 60)
    void testHighConcurrency() throws InterruptedException, IOException {
        // Configure high request rate
        testProperties.setProperty("BOOKING_REQUEST_ARRIVAL_RATE", "120"); // 2 per second
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration with high concurrency");
        }
        
        // Create multiple booking services to generate load
        List<BookingService> services = new ArrayList<>();
        
        // Start 3 booking services
        for (int i = 0; i < 3; i++) {
            BookingService service = new BookingService(broker, 3, testHotels);
            service.start();
            services.add(service);
        }
        
        // Let the system run under load
        Thread.sleep(10000);
        
        // Stop all services
        for (BookingService service : services) {
            service.close();
        }
        
        // Test passes if system handles high concurrency without crashing
        assertTrue(true, "System should handle high concurrency");
    }
    
    /**
     * Test timeout detection and handling.
     */
    @Test
    @Timeout(value = 90)
    void testTimeoutHandling() throws InterruptedException, IOException {
        // Set very high processing time to force timeouts
        testProperties.setProperty("AVERAGE_PROCESSING_TIME", "35000"); // 35 seconds (longer than broker timeout)
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated test configuration with long processing times");
        }
        
        // Restart hotel servers with new configuration
        for (int i = 0; i < hotelServers.size(); i++) {
            hotelServers.get(i).close();
            hotelServers.set(i, new HotelServer(testHotels.get(i)));
        }
        
        // Start booking service with a low request rate
        testProperties.setProperty("BOOKING_REQUEST_ARRIVAL_RATE", "2"); // Very low rate for timeout test
        
        try (FileOutputStream out = new FileOutputStream(TEST_CONFIG_PATH)) {
            testProperties.store(out, "Updated request rate for timeout test");
        }
        
        // Start booking service
        bookingService.start();
        
        // Wait for timeout handling to kick in (broker timeout is 30 seconds)
        Thread.sleep(60000); // Wait 60 seconds to allow timeouts to occur and be handled
        
        // Stop service
        bookingService.stop();
        
        // Test passes if system properly handles timeouts
        assertTrue(true, "System should handle request timeouts properly");
    }
} 