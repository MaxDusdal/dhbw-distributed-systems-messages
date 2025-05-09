package com.travelbroker.broker;

import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.model.BookingStatus;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Singleton central orchestrator for processing trip bookings.
 * Implements asynchronous processing to coordinate hotel bookings.
 */
public class TravelBroker implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TravelBroker.class);
    private static final String BROKER_ENDPOINT = "tcp://*:5555";
    private static final String BROKER_CLIENT_ENDPOINT = "tcp://localhost:5555";
    
    // Processor thread pool for handling booking requests
    private final ExecutorService processorThreadPool;
    
    // Socket for communication with BookingServices
    private final ZeroMQClient bookingServiceSocket;
    
    // Map to store pending booking requests and their state
    private final Map<UUID, PendingBooking> pendingBookings;
    
    private boolean isRunning = false;
    
    /**
     * Creates a new TravelBroker.
     */
    public TravelBroker() {
        // Use ROUTER socket type for asynchronous communication
        this.bookingServiceSocket = new ZeroMQClient(BROKER_ENDPOINT, SocketType.ROUTER);
        
        // Thread pool for processing booking requests
        this.processorThreadPool = Executors.newFixedThreadPool(10);
        
        // Thread-safe map to store pending bookings
        this.pendingBookings = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets the client-side endpoint for the TravelBroker.
     * This is the endpoint that clients should use to connect to the broker.
     *
     * @return The client-side endpoint
     */
    public String getClientEndpoint() {
        return BROKER_CLIENT_ENDPOINT;
    }
    
    /**
     * Starts the TravelBroker to listen for booking requests.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        try {
            bookingServiceSocket.bind();
            isRunning = true;
            
            // Start listening for booking requests
            bookingServiceSocket.listenForResponses(this::handleIncomingMessage);
            
            logger.info("TravelBroker started and listening for booking requests on {}", BROKER_ENDPOINT);
        } catch (Exception e) {
            logger.error("Failed to start TravelBroker: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Stops the TravelBroker.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        // Shutdown the processor thread pool
        processorThreadPool.shutdown();
        try {
            if (!processorThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                processorThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            processorThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("TravelBroker stopped");
    }
    
    /**
     * Handles an incoming message from a BookingService.
     * 
     * @param message The message, which may contain routing information and the actual content
     */
    private void handleIncomingMessage(String message) {
        try {
            // For ROUTER sockets, the message format is: identityFrame\0\0contentFrame
            String[] frames = message.split("\0\0", 2);
            if (frames.length < 2) {
                logger.error("Invalid message format: {}", message);
                return;
            }
            
            String routingId = frames[0];
            String content = frames[1];
            
            // Parse the booking request JSON
            BookingRequest bookingRequest = JsonUtil.fromJson(content, BookingRequest.class);
            
            if (bookingRequest != null && bookingRequest.getBookingId() != null) {
                // Log the received booking request
                logger.info("Received booking request: BookingId={}, CustomerId={}, Hotels={}",
                        bookingRequest.getBookingId(), 
                        bookingRequest.getCustomerId(),
                        bookingRequest.getHotelBookings().length);
                
                // Process the booking request asynchronously
                processorThreadPool.submit(() -> processBookingRequestAsync(bookingRequest, routingId));
            }
        } catch (Exception e) {
            logger.error("Error handling incoming message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Processes a booking request asynchronously.
     * This would interact with hotel servers to check availability and make bookings.
     * 
     * @param bookingRequest The booking request to process
     * @param routingId The routing ID of the client that sent the request
     */
    private void processBookingRequestAsync(BookingRequest bookingRequest, String routingId) {
        UUID bookingId = bookingRequest.getBookingId();
        
        // Create a new pending booking entry
        PendingBooking pendingBooking = new PendingBooking(bookingId, bookingRequest, routingId);
        pendingBookings.put(bookingId, pendingBooking);
        
        try {
            // Update booking status to PROCESSING
            pendingBooking.setStatus(BookingStatus.PROCESSING);
            
            // In a real implementation, here we would:
            // 1. Send requests to all hotel servers for the hotels in the booking
            // 2. Each hotel server would respond asynchronously
            // 3. When all responses are received, we would determine the final status
            // 4. If all succeed, confirm the booking
            // 5. If any fail, initiate compensation (rollback) for successful bookings
            
            // For now, simulate processing with a delay and always succeed
            // This simulates the asynchronous nature of real hotel server communication
            simulateHotelProcessing(pendingBooking);
        } catch (Exception e) {
            logger.error("Error processing booking request {}: {}", bookingId, e.getMessage(), e);
            
            // Set status to FAILED and send response
            pendingBooking.setStatus(BookingStatus.FAILED);
            sendBookingResponse(pendingBooking, "Processing error: " + e.getMessage());
            
            // Remove from pending bookings
            pendingBookings.remove(bookingId);
        }
    }
    
    /**
     * Simulates processing by hotel servers.
     * In a real implementation, this would send requests to actual hotel servers.
     * 
     * @param pendingBooking The pending booking to process
     */
    private void simulateHotelProcessing(PendingBooking pendingBooking) {
        // Submit a task to simulate delayed processing
        processorThreadPool.submit(() -> {
            try {
                // Simulate processing time (200-1000ms)
                Thread.sleep(200 + (long)(Math.random() * 2000));
                
                // Simulate 90% success rate
                boolean success = Math.random() < 0.9;
                
                if (success) {
                    // All hotels available, confirm booking
                    pendingBooking.setStatus(BookingStatus.CONFIRMED);
                    sendBookingResponse(pendingBooking, "All hotel bookings confirmed");
                } else {
                    // Some hotel not available, initiate rollback
                    pendingBooking.setStatus(BookingStatus.ROLLING_BACK);
                    
                    // Simulate rollback
                    Thread.sleep(100 + (long)(Math.random() * 400));
                    
                    pendingBooking.setStatus(BookingStatus.FAILED);
                    sendBookingResponse(pendingBooking, "Some hotels unavailable, booking rolled back");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pendingBooking.setStatus(BookingStatus.FAILED);
                sendBookingResponse(pendingBooking, "Processing interrupted");
            } finally {
                // Remove from pending bookings
                pendingBookings.remove(pendingBooking.getBookingId());
            }
        });
    }
    
    /**
     * Sends a booking response back to the BookingService.
     * 
     * @param pendingBooking The pending booking to respond to
     * @param message The message to include in the response
     */
    private void sendBookingResponse(PendingBooking pendingBooking, String message) {
        // Create the response
        BookingResponse response = new BookingResponse(
                pendingBooking.getBookingId(),
                pendingBooking.getStatus().toString(),
                message
        );
        
        // Convert to JSON
        String responseJson = JsonUtil.toJson(response);
        
        // Format message for ROUTER socket to send to DEALER client
        // Format: routingId\0\0responseJson
        String routingId = pendingBooking.getRoutingId();
        String framedMessage = routingId + "\0\0" + responseJson;
        
        boolean sent = bookingServiceSocket.sendRequest(framedMessage);
        
        if (sent) {
            logger.info("Sent response for booking {}: Status={}, Message={}",
                    pendingBooking.getBookingId(),
                    pendingBooking.getStatus(),
                    message);
        } else {
            logger.error("Failed to send response for booking {}", pendingBooking.getBookingId());
        }
    }
    
    /**
     * Class to track the state of a pending booking.
     */
    private static class PendingBooking {
        private final UUID bookingId;
        private final BookingRequest request;
        private final String routingId;
        private BookingStatus status;
        private final Map<String, Boolean> hotelResponses = new HashMap<>();
        
        public PendingBooking(UUID bookingId, BookingRequest request, String routingId) {
            this.bookingId = bookingId;
            this.request = request;
            this.routingId = routingId;
            this.status = BookingStatus.PENDING;
            
            // Initialize hotel responses
            for (BookingRequest.HotelBookingDTO hotelBooking : request.getHotelBookings()) {
                hotelResponses.put(hotelBooking.getHotelId(), null); // null means no response yet
            }
        }
        
        public UUID getBookingId() {
            return bookingId;
        }
        
        public BookingRequest getRequest() {
            return request;
        }
        
        public String getRoutingId() {
            return routingId;
        }
        
        public BookingStatus getStatus() {
            return status;
        }
        
        public void setStatus(BookingStatus status) {
            this.status = status;
        }
        
        public Map<String, Boolean> getHotelResponses() {
            return hotelResponses;
        }
        
        public void setHotelResponse(String hotelId, boolean available) {
            hotelResponses.put(hotelId, available);
        }
        
        public boolean areAllHotelsResponded() {
            return !hotelResponses.containsValue(null);
        }
        
        public boolean areAllHotelsAvailable() {
            return !hotelResponses.containsValue(false);
        }
    }
    
    /**
     * Closes the TravelBroker and releases resources.
     */
    @Override
    public void close() {
        stop();
        if (bookingServiceSocket != null) {
            bookingServiceSocket.close();
        }
    }
}
