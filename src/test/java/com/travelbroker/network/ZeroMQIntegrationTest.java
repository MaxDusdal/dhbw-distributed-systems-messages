package com.travelbroker.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ZeroMQClient that simulates real-world usage scenarios
 * with the TravelBroker and hotel services.
 */
class ZeroMQIntegrationTest {
    private static final String BROKER_ENDPOINT = "tcp://localhost:5560";
    private static final String HOTEL_ENDPOINT = "tcp://localhost:5561";
    
    // Broker components
    private ZeroMQClient brokerRouter;  // Handles client requests (ROUTER)
    private ZeroMQClient brokerDealer;  // Communicates with hotel (DEALER)
    
    // Hotel components
    private ZeroMQClient hotelRouter;   // Hotel service (ROUTER)
    
    // Client components
    private ZeroMQClient clientDealer;  // Client sending requests (DEALER)
    
    @BeforeEach
    void setUp() {
        // Create broker components
        brokerRouter = new ZeroMQClient(BROKER_ENDPOINT, SocketType.ROUTER);
        brokerDealer = new ZeroMQClient(HOTEL_ENDPOINT, SocketType.DEALER);
        
        // Create hotel components
        hotelRouter = new ZeroMQClient(HOTEL_ENDPOINT, SocketType.ROUTER);
        
        // Create client components
        clientDealer = new ZeroMQClient(BROKER_ENDPOINT, SocketType.DEALER);
        
        // Bind ROUTER sockets
        brokerRouter.bind();
        hotelRouter.bind();
        
        // Connect DEALER sockets
        brokerDealer.connect();
        clientDealer.connect();
    }
    
    @AfterEach
    void tearDown() {
        // Close all components
        if (brokerRouter != null) brokerRouter.close();
        if (brokerDealer != null) brokerDealer.close();
        if (hotelRouter != null) hotelRouter.close();
        if (clientDealer != null) clientDealer.close();
    }
    
    @Test
    @Timeout(10)
    void testCompleteBookingFlow() throws InterruptedException {
        // Test a complete booking flow from client -> broker -> hotel -> broker -> client
        
        // 1. Set up a latch to track the complete flow
        CountDownLatch requestReceivedByBroker = new CountDownLatch(1);
        CountDownLatch requestReceivedByHotel = new CountDownLatch(1);
        CountDownLatch responseReceivedByBroker = new CountDownLatch(1);
        CountDownLatch responseReceivedByClient = new CountDownLatch(1);
        
        // 2. Store identity and messages for verification
        AtomicReference<String> clientIdentity = new AtomicReference<>();
        AtomicReference<String> brokerIdentity = new AtomicReference<>();
        AtomicReference<String> clientRequestContent = new AtomicReference<>();
        AtomicReference<String> hotelResponseContent = new AtomicReference<>();
        AtomicReference<String> finalResponseToClient = new AtomicReference<>();
        
        // 3. Set up broker to receive client requests and forward to hotel
        brokerRouter.listenForResponses(message -> {
            // Broker receives client request
            String[] frames = message.split("\0\0", 2);
            clientIdentity.set(frames[0]);
            clientRequestContent.set(frames[1]);
            requestReceivedByBroker.countDown();
            
            // Forward the request to the hotel with the original content
            brokerDealer.sendRequest(frames[1]);
        });
        
        // 4. Set up hotel to receive requests from broker and send response
        hotelRouter.listenForResponses(message -> {
            // Hotel receives broker request
            String[] frames = message.split("\0\0", 2);
            brokerIdentity.set(frames[0]);
            requestReceivedByHotel.countDown();
            
            // Send response back to broker
            String responseContent = "BOOKING_CONFIRMED:" + UUID.randomUUID().toString();
            hotelResponseContent.set(responseContent);
            String framedResponse = frames[0] + "\0\0" + responseContent;
            hotelRouter.sendRequest(framedResponse);
        });
        
        // 5. Set up broker to receive hotel responses and forward to client
        brokerDealer.listenForResponses(message -> {
            // Broker receives hotel response
            responseReceivedByBroker.countDown();
            
            // Forward response to the client
            String framedResponse = clientIdentity.get() + "\0\0" + message;
            finalResponseToClient.set(message);
            brokerRouter.sendRequest(framedResponse);
        });
        
        // 6. Set up client to receive broker responses
        clientDealer.listenForResponses(message -> {
            responseReceivedByClient.countDown();
        });
        
        // 7. Start the flow by sending a client request
        String bookingRequest = "{\"type\":\"BOOKING_REQUEST\",\"bookingId\":\"" + UUID.randomUUID() + 
                               "\",\"hotelName\":\"Grand Hotel\",\"guestName\":\"John Doe\",\"roomCount\":1}";
        
        clientDealer.sendRequest(bookingRequest);
        
        // 8. Verify the complete flow is successful
        assertTrue(requestReceivedByBroker.await(2, TimeUnit.SECONDS), "Broker should receive client request");
        assertTrue(requestReceivedByHotel.await(2, TimeUnit.SECONDS), "Hotel should receive broker request");
        assertTrue(responseReceivedByBroker.await(2, TimeUnit.SECONDS), "Broker should receive hotel response");
        assertTrue(responseReceivedByClient.await(2, TimeUnit.SECONDS), "Client should receive broker response");
        
        // 9. Verify message content is correctly passed through the system
        assertNotNull(clientRequestContent.get(), "Client request content should not be null");
        assertEquals(bookingRequest, clientRequestContent.get(), "Client request content should match original request");
        assertNotNull(hotelResponseContent.get(), "Hotel response content should not be null");
        assertTrue(hotelResponseContent.get().startsWith("BOOKING_CONFIRMED:"), "Hotel response should be a booking confirmation");
        assertEquals(hotelResponseContent.get(), finalResponseToClient.get(), "Final response to client should match hotel response");
    }
    
    @Test
    @Timeout(15)
    void testConcurrentMessageHandling() throws InterruptedException {
        // Test the system's ability to handle multiple concurrent messages
        final int MESSAGE_COUNT = 20;
        
        // Set up counters and latches
        CountDownLatch allMessagesProcessed = new CountDownLatch(MESSAGE_COUNT);
        AtomicInteger messagesReceivedByBroker = new AtomicInteger(0);
        AtomicInteger messagesReceivedByHotel = new AtomicInteger(0);
        AtomicInteger responsesReceivedByClient = new AtomicInteger(0);
        
        // Set up broker to receive client requests
        AtomicReference<String> lastClientIdentity = new AtomicReference<>();
        brokerRouter.listenForResponses(message -> {
            String[] frames = message.split("\0\0", 2);
            lastClientIdentity.set(frames[0]);
            messagesReceivedByBroker.incrementAndGet();
            
            // Forward to hotel
            brokerDealer.sendRequest(frames[1]);
        });
        
        // Set up hotel to receive broker requests
        hotelRouter.listenForResponses(message -> {
            String[] frames = message.split("\0\0", 2);
            messagesReceivedByHotel.incrementAndGet();
            
            // Send response with a small delay to simulate processing
            new Thread(() -> {
                try {
                    Thread.sleep(50); // Small delay
                    String responseContent = "BOOKING_CONFIRMED:" + UUID.randomUUID().toString();
                    String framedResponse = frames[0] + "\0\0" + responseContent;
                    hotelRouter.sendRequest(framedResponse);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
        
        // Set up broker to receive hotel responses
        brokerDealer.listenForResponses(hotelResponse -> {
            // Forward to client
            String framedResponse = lastClientIdentity.get() + "\0\0" + hotelResponse;
            brokerRouter.sendRequest(framedResponse);
        });
        
        // Set up client to receive broker responses
        clientDealer.listenForResponses(response -> {
            responsesReceivedByClient.incrementAndGet();
            allMessagesProcessed.countDown();
        });
        
        // Send multiple requests from client
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            String bookingId = UUID.randomUUID().toString();
            String bookingRequest = "{\"type\":\"BOOKING_REQUEST\",\"bookingId\":\"" + bookingId + 
                                   "\",\"hotelName\":\"Hotel " + i + "\",\"roomCount\":1}";
            clientDealer.sendRequest(bookingRequest);
            
            // Small delay between requests to prevent overwhelming the system
            Thread.sleep(50);
        }
        
        // Wait for all messages to be processed
        assertTrue(allMessagesProcessed.await(10, TimeUnit.SECONDS), 
                  "All messages should be processed within the timeout");
        
        // Verify all messages were processed
        assertEquals(MESSAGE_COUNT, messagesReceivedByBroker.get(), "Broker should receive all client requests");
        assertEquals(MESSAGE_COUNT, messagesReceivedByHotel.get(), "Hotel should receive all broker requests");
        assertEquals(MESSAGE_COUNT, responsesReceivedByClient.get(), "Client should receive all responses");
    }
    
    @Test
    @Timeout(10)
    void testErrorHandling() throws InterruptedException {
        // Test that the system properly handles errors
        CountDownLatch errorResponseReceived = new CountDownLatch(1);
        AtomicReference<String> errorResponse = new AtomicReference<>();
        AtomicReference<String> clientIdentity = new AtomicReference<>();
        
        // Set up broker to receive client requests
        brokerRouter.listenForResponses(message -> {
            String[] frames = message.split("\0\0", 2);
            clientIdentity.set(frames[0]);
            
            // Check if this is an error-triggering request
            if (frames[1].contains("ERROR_TRIGGER")) {
                // Send error response directly back to client
                String errorMsg = "{\"status\":\"ERROR\",\"message\":\"Invalid request format\"}";
                String framedResponse = frames[0] + "\0\0" + errorMsg;
                brokerRouter.sendRequest(framedResponse);
            } else {
                // Forward normal requests to hotel
                brokerDealer.sendRequest(frames[1]);
            }
        });
        
        // Set up client to receive responses
        clientDealer.listenForResponses(response -> {
            errorResponse.set(response);
            errorResponseReceived.countDown();
        });
        
        // Send an error-triggering request
        String errorRequest = "{\"type\":\"ERROR_TRIGGER\",\"bookingId\":\"" + UUID.randomUUID() + "\"}";
        clientDealer.sendRequest(errorRequest);
        
        // Verify error handling
        assertTrue(errorResponseReceived.await(5, TimeUnit.SECONDS), "Error response should be received");
        assertNotNull(errorResponse.get(), "Error response should not be null");
        assertTrue(errorResponse.get().contains("ERROR"), "Response should contain ERROR status");
    }
} 