package com.travelbroker.broker;

import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.model.BookingStatus;
import com.travelbroker.network.MultipleHotelDealerSocket;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Singleton central orchestrator for processing trip bookings.
 * Implements asynchronous processing to coordinate hotel bookings.
 */
public class TravelBroker implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TravelBroker.class);
    private static final String BROKER_ENDPOINT = "tcp://*:5555";
    private static final String BROKER_CLIENT_ENDPOINT = "tcp://localhost:5555";

    private final ScheduledExecutorService timeoutScheduler;
    private static final long BOOKING_TIMEOUT_MS = 30000; // 30 Sekunden
    private static final long CLEANUP_INTERVAL_MS = 5000; // Alle 5 Sekunden prüfen
    
    // Processor thread pool for handling booking requests
    private final ExecutorService processorThreadPool;
    
    // Socket for communication with BookingServices
    private final ZeroMQClient bookingServiceSocket;

    private final MultipleHotelDealerSocket hotelDealerSocket;
    
    // Map to store pending booking requests and their state
    private final Map<UUID, PendingBooking> pendingBookings;

    private Map<String, String> hotelEndpoints;
    
    private boolean isRunning = false;
    
    /**
     * Creates a new TravelBroker.
     */
    public TravelBroker() {
        // Use ROUTER socket type for asynchronous communication
        this.bookingServiceSocket = new ZeroMQClient(BROKER_ENDPOINT, SocketType.ROUTER);
        this.hotelDealerSocket = new MultipleHotelDealerSocket();
        
        // Thread pool for processing booking requests
        this.processorThreadPool = Executors.newFixedThreadPool(10);
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Thread-safe map to store pending bookings
        this.pendingBookings = new ConcurrentHashMap<>();
        this.hotelEndpoints = Map.of(
                "H1", "tcp://localhost:6001",
                "H2", "tcp://localhost:6002",
                "H3", "tcp://localhost:6003",
                "H4", "tcp://localhost:6004",
                "H5", "tcp://localhost:6005"
        );
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
            hotelDealerSocket.setMessageHandler(this::handleHotelResponse);
            hotelDealerSocket.addEndpoints(hotelEndpoints);
            hotelDealerSocket.start();

            timeoutScheduler.scheduleAtFixedRate(
                    this::checkForTimeouts,
                    CLEANUP_INTERVAL_MS,
                    CLEANUP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
            
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

        timeoutScheduler.shutdownNow();
        hotelDealerSocket.stop();
        
        logger.info("TravelBroker stopped");
    }

    /**
     * Prüft regelmäßig alle ausstehenden Buchungen auf Timeouts.
     */
    private void checkForTimeouts() {
        long currentTime = System.currentTimeMillis();

        // Iterieren über eine Kopie, um ConcurrentModificationExceptions zu vermeiden
        new HashMap<>(pendingBookings).forEach((bookingId, booking) -> {
            // Prüfen, ob die Buchung ein Timeout hat
            if (currentTime - booking.getCreationTime() > BOOKING_TIMEOUT_MS) {
                if (!booking.areAllHotelsResponded()) {
                    logger.warn("Timeout waiting for hotel responses for booking: {}", bookingId);

                    // Status aktualisieren
                    booking.setStatus(BookingStatus.FAILED);
                    sendBookingResponse(booking, "Timeout waiting for hotel responses");
                    pendingBookings.remove(bookingId);
                }
            }
        });
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
    /**
     * Processes a booking request asynchronously.
     * Sends requests to all hotels included in the booking.
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

            // Send booking requests to all hotels
            boolean allRequestsSent = true;
            for (BookingRequest.HotelBookingDTO hotelBooking : bookingRequest.getHotelBookings()) {
                String hotelId = hotelBooking.getHotelId();

                // Convert the booking request to JSON
                String messageJson = JsonUtil.toJson(hotelBooking);

                try {
                    // Check if we have an endpoint for this hotel
                    if (!hotelDealerSocket.hasEndpoint(hotelId)) {
                        logger.error("No endpoint found for hotel: {}", hotelId);
                        pendingBooking.setHotelResponse(hotelId, false);
                        allRequestsSent = false;
                        continue;
                    }

                    // Send the booking request to the hotel
                    hotelDealerSocket.sendMessage(hotelId, messageJson);
                    logger.info("Sent booking request to hotel {}: BookingId={}, TimeBlock={}",
                            hotelId, bookingId, hotelBooking.getTimeBlock());
                } catch (Exception e) {
                    logger.error("Error sending booking request to hotel {}: {}", hotelId, e.getMessage(), e);
                    pendingBooking.setHotelResponse(hotelId, false);
                    allRequestsSent = false;
                }
            }

            // If we failed to send requests to any hotels, fail the booking immediately
            if (!allRequestsSent && pendingBooking.areAllHotelsResponded() && !pendingBooking.areAllHotelsAvailable()) {
                pendingBooking.setStatus(BookingStatus.FAILED);
                sendBookingResponse(pendingBooking, "Failed to communicate with all hotels");
                pendingBookings.remove(bookingId);
            }

            // Wir brauchen keinen individuellen Timeout mehr, da der zentrale Timeout-Mechanismus dies übernimmt

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
     * Verarbeitet die Antwort eines Hotels auf eine Buchungsanfrage.
     *
     * @param hotelId Die ID des Hotels, von dem die Antwort stammt
     * @param message Die Antwortnachricht vom Hotel
     */
    private void handleHotelResponse(String hotelId, String message) {
        logger.debug("Antwort von Hotel erhalten: {}: {}", hotelId, message);

        try {
            // Antwort von JSON in HotelResponse-Objekt umwandeln
            HotelResponse hotelResponse = JsonUtil.fromJson(message, HotelResponse.class);

            if (hotelResponse == null || hotelResponse.getBookingId() == null) {
                logger.error("Ungültige Hotel-Antwort von {}: {}", hotelId, message);
                return;
            }

            UUID bookingId = hotelResponse.getBookingId();
            boolean available = hotelResponse.isAvailable();

            // Die zugehörige Buchung finden
            PendingBooking pendingBooking = pendingBookings.get(bookingId);
            if (pendingBooking == null) {
                logger.warn("Antwort für unbekannte oder bereits abgeschlossene Buchung erhalten: {} von Hotel {}",
                        bookingId, hotelId);
                return;
            }

            // Status für dieses Hotel aktualisieren
            synchronized (pendingBooking) {
                pendingBooking.setHotelResponse(hotelId, available);

                // Prüfen, ob alle Hotels geantwortet haben
                if (pendingBooking.areAllHotelsResponded()) {
                    processCompletedBooking(pendingBooking);
                }
            }
        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung der Hotel-Antwort von {}: {}", hotelId, e.getMessage(), e);
        }
    }

    /**
     * Verarbeitet eine Buchung, für die alle Hotel-Antworten eingegangen sind.
     *
     * @param pendingBooking Die zu verarbeitende Buchung
     */
    private void processCompletedBooking(PendingBooking pendingBooking) {
        UUID bookingId = pendingBooking.getBookingId();

        // Prüfen, ob alle Hotels verfügbar sind
        if (pendingBooking.areAllHotelsAvailable()) {
            // Alle Hotels sind verfügbar, Buchung bestätigen
            pendingBooking.setStatus(BookingStatus.CONFIRMED);
            sendBookingResponse(pendingBooking, "Alle Hotelbuchungen bestätigt");
            logger.info("Buchung {} erfolgreich bestätigt", bookingId);
        } else {
            // Mindestens ein Hotel ist nicht verfügbar, Buchung zurückrollen
            pendingBooking.setStatus(BookingStatus.ROLLING_BACK);

            // Rollback für alle bestätigten Hotels durchführen
            boolean rollbackNeeded = false;
            for (Map.Entry<String, Boolean> entry : pendingBooking.getHotelResponses().entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    rollbackNeeded = true;
                    sendCancellationToHotel(pendingBooking.getBookingId(), entry.getKey());
                }
            }

            if (rollbackNeeded) {
                logger.info("Rollback für Buchung {} initiiert", bookingId);
                // Für einen vollständigen Rollback könnten wir hier einen Timer einrichten,
                // aber zur Vereinfachung setzen wir den Status sofort auf FAILED
            }

            pendingBooking.setStatus(BookingStatus.FAILED);
            sendBookingResponse(pendingBooking, "Einige Hotels nicht verfügbar, Buchung storniert");
            logger.info("Buchung {} fehlgeschlagen wegen nicht verfügbarer Hotels", bookingId);
        }

        // Buchung aus der Liste der ausstehenden Buchungen entfernen
        pendingBookings.remove(bookingId);
    }

    /**
     * Sendet eine Stornierungsnachricht an ein Hotel.
     *
     * @param bookingId Die ID der zu stornierenden Buchung
     * @param hotelId Die ID des Hotels
     */
    private void sendCancellationToHotel(UUID bookingId, String hotelId) {
        try {
            CancellationRequest cancellation = new CancellationRequest(bookingId);
            String messageJson = JsonUtil.toJson(cancellation);

            hotelDealerSocket.sendMessage(hotelId, messageJson);
            logger.info("Stornierungsanfrage an Hotel {} für Buchung {} gesendet", hotelId, bookingId);
        } catch (Exception e) {
            logger.error("Fehler beim Senden der Stornierungsanfrage an Hotel {}: {}", hotelId, e.getMessage(), e);
        }
    }

    /**
     * Datenklasse für Hotel-Antworten.
     */
    private static class HotelResponse {
        private UUID bookingId;
        private boolean available;

        // Default-Konstruktor für JSON-Deserialisierung
        public HotelResponse() {
        }

        public HotelResponse(UUID bookingId, boolean available) {
            this.bookingId = bookingId;
            this.available = available;
        }

        public UUID getBookingId() {
            return bookingId;
        }

        public boolean isAvailable() {
            return available;
        }
    }

    /**
     * Datenklasse für Stornierungsanfragen.
     */
    private static class CancellationRequest {
        private final UUID bookingId;

        public CancellationRequest(UUID bookingId) {
            this.bookingId = bookingId;
        }

        public UUID getBookingId() {
            return bookingId;
        }
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
        private final long creationTime;
        
        public PendingBooking(UUID bookingId, BookingRequest request, String routingId) {
            this.bookingId = bookingId;
            this.request = request;
            this.routingId = routingId;
            this.status = BookingStatus.PENDING;
            this.creationTime = System.currentTimeMillis();
            
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
        public long getCreationTime() {
            return creationTime;
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
