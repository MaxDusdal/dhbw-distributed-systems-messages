package com.travelbroker.booking;

import com.travelbroker.broker.TravelBroker;
import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.model.Hotel;
import com.travelbroker.model.HotelBooking;
import com.travelbroker.model.TripBooking;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.ConfigProvider;
import com.travelbroker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client system that generates booking requests at a configured rate
 * and logs results.
 */
public class BookingService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final AtomicInteger instanceCounter = new AtomicInteger(0);
    
    private final int instanceId;
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ZeroMQClient client;
    private final Properties config;
    private final int requestsPerMinute;
    private final long intervalBetweenRequests;
    private final List<Hotel> availableHotels;
    
    // Map to track pending requests by booking ID
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    
    private boolean isRunning = false;
    
    /**
     * Creates a new BookingService with the specified TravelBroker and hotels.
     *
     * @param travelBroker    The TravelBroker to send booking requests to
     * @param totalInstances  The total number of BookingService instances
     * @param availableHotels The list of available hotels to book
     */
    public BookingService(TravelBroker travelBroker, int totalInstances, List<Hotel> availableHotels) {
        this.instanceId = instanceCounter.incrementAndGet();
        this.availableHotels = availableHotels;
        
        if (availableHotels == null || availableHotels.isEmpty()) {
            throw new IllegalArgumentException("Available hotels cannot be null or empty");
        }
        
        String brokerEndpoint = travelBroker.getClientEndpoint();
        
        this.client = new ZeroMQClient(brokerEndpoint, SocketType.DEALER);
        
        this.config = ConfigProvider.loadConfiguration();
        
        int totalRequestsPerMinute = Integer.parseInt(config.getProperty("BOOKING_REQUEST_ARRIVAL_RATE", "100"));
        this.requestsPerMinute = totalRequestsPerMinute / totalInstances;
        
        this.intervalBetweenRequests = Duration.ofMinutes(1).toMillis() / requestsPerMinute;
        
        logger.info(
                "BookingService#{} initialized with {} requests/minute (interval: {} ms), {} hotels available, connecting to broker at {}",
                instanceId, requestsPerMinute, intervalBetweenRequests, availableHotels.size(), brokerEndpoint);
    }
    
    /**
     * Starts sending booking requests at the configured rate.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        
        try {
            client.connect();
            isRunning = true;
            
            // Start the response listener
            client.listenForResponses(this::handleBookingResponse);
            
            // Schedule booking requests
            scheduler.scheduleAtFixedRate(
                    this::sendRandomBookingRequest,
                    0,
                    intervalBetweenRequests,
                    TimeUnit.MILLISECONDS);
            
            logger.info("BookingService#{} started sending requests", instanceId);
        } catch (Exception e) {
            logger.error("Failed to start BookingService: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Stops sending booking requests.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("BookingService#{} stopped", instanceId);
    }
    
    /**
     * Sends a random booking request to the TravelBroker.
     */
    private void sendRandomBookingRequest() {
        try {
            // Generate a random trip booking
            TripBooking booking = generateRandomTripBooking();
            UUID bookingId = booking.getBookingId();
            
            // Convert trip booking to DTO for JSON serialization
            BookingRequest bookingRequest = convertTripBookingToDto(booking);
            
            // Store the pending request
            pendingRequests.put(bookingId, new PendingRequest(booking));
            
            // Convert DTO to JSON
            String bookingJson = JsonUtil.toJson(bookingRequest);
            
            // Send the booking request asynchronously
            logger.info("BookingService#{} sending booking request: {}", instanceId, bookingId);
            boolean sent = client.sendRequest(bookingJson);
            
            if (!sent) {
                logger.error("Failed to send booking request: {}", bookingId);
                pendingRequests.remove(bookingId);
            }
        } catch (Exception e) {
            logger.error("Error sending booking request: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles the response from the TravelBroker for a booking request.
     *
     * @param response The JSON response from the TravelBroker
     */
    private void handleBookingResponse(String response) {
        try {
            BookingResponse bookingResponse = JsonUtil.fromJson(response, BookingResponse.class);
            
            if (bookingResponse == null || bookingResponse.getBookingId() == null) {
                logger.error("Invalid response format or missing booking ID: {}", response);
                return;
            }
            
            UUID bookingId = bookingResponse.getBookingId();
       
            // Get the pending request
            PendingRequest pendingRequest = pendingRequests.get(bookingId);
            if (pendingRequest == null) {
                logger.warn("Received response for unknown booking: {}", bookingId);
                return;
            }
            
            // Update the status of the pending request
            pendingRequest.setStatus(bookingResponse.getStatus());
            pendingRequest.setResponseTime(System.currentTimeMillis());
            
            logger.info("BookingService#{} received response for booking {}: Status={}, Message={}, Response Time={}ms",
                    instanceId, bookingId, bookingResponse.getStatus(), 
                    bookingResponse.getMessage(),
                    pendingRequest.getResponseTime() - pendingRequest.getRequestTime());
            
            // Remove the pending request
            pendingRequests.remove(bookingId);
            
            // In a real implementation, we would update the booking in a persistent store
        } catch (Exception e) {
            logger.error("Error processing booking response: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Converts a TripBooking to a BookingRequest DTO.
     *
     * @param tripBooking The trip booking to convert
     * @return The BookingRequest DTO
     */
    private BookingRequest convertTripBookingToDto(TripBooking tripBooking) {
        HotelBooking[] hotelBookings = tripBooking.getHotelBookings();
        BookingRequest.HotelBookingDTO[] hotelBookingDtos = new BookingRequest.HotelBookingDTO[hotelBookings.length];
        
        for (int i = 0; i < hotelBookings.length; i++) {
            HotelBooking hb = hotelBookings[i];
            hotelBookingDtos[i] = new BookingRequest.HotelBookingDTO(
                    hb.getBookingId(),
                    hb.getHotelId(),
                    hb.getTimeBlock());
        }
        
        return new BookingRequest(
                tripBooking.getBookingId(),
                tripBooking.getCustomerId(),
                tripBooking.getStatus().toString(),
                hotelBookingDtos);
    }
    
    /**
     * Generates a random trip booking with random hotel bookings.
     * Ensures consecutive bookings are not for the same hotel.
     *
     * @return A random trip booking
     */
    private TripBooking generateRandomTripBooking() {
        UUID customerId = UUID.randomUUID();
        
        int numHotels = random.nextInt(5) + 1;
        HotelBooking[] hotelBookings = new HotelBooking[numHotels];
        
        int timeBlock = random.nextInt(100) + 1;
        
        Hotel lastHotel = null;
        
        for (int i = 0; i < numHotels; i++) {
            Hotel selectedHotel;
            do {
                selectedHotel = availableHotels.get(random.nextInt(availableHotels.size()));
            } while (lastHotel != null && selectedHotel.equals(lastHotel));
            
            lastHotel = selectedHotel;
            
            hotelBookings[i] = new HotelBooking(UUID.randomUUID(), selectedHotel.getId(), timeBlock);
            
            timeBlock = Math.min(timeBlock + 1, 100);
        }
        
        return new TripBooking(hotelBookings, customerId);
    }
    
    /**
     * Class to track a pending booking request.
     */
    private static class PendingRequest {
        private final TripBooking tripBooking;
        private final long requestTime;
        private long responseTime;
        private String status;
        
        public PendingRequest(TripBooking tripBooking) {
            this.tripBooking = tripBooking;
            this.requestTime = System.currentTimeMillis();
        }
        
        public TripBooking getTripBooking() {
            return tripBooking;
        }
        
        public long getRequestTime() {
            return requestTime;
        }
        
        public long getResponseTime() {
            return responseTime;
        }
        
        public void setResponseTime(long responseTime) {
            this.responseTime = responseTime;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
    }
    
    @Override
    public void close() {
        stop();
        if (client != null) {
            client.close();
        }
    }
}