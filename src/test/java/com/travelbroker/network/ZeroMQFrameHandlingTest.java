package com.travelbroker.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that focuses on proper frame handling in ZeroMQ communication.
 * This is crucial for correct operation of the DEALER/ROUTER pattern.
 */
class ZeroMQFrameHandlingTest {
    private static final String TEST_ENDPOINT = "tcp://localhost:5562";
    private ZeroMQClient dealer;
    private ZeroMQClient router;

    // For direct ZMQ testing
    private ZContext context;
    private ZMQ.Socket rawDealer;
    private ZMQ.Socket rawRouter;

    @BeforeEach
    void setUp() {
        // Create ZeroMQClient instances
        router = new ZeroMQClient(TEST_ENDPOINT, SocketType.ROUTER);
        dealer = new ZeroMQClient(TEST_ENDPOINT, SocketType.DEALER);
        
        // Set up raw ZeroMQ sockets for direct frame testing
        context = new ZContext();
        rawRouter = context.createSocket(SocketType.ROUTER);
        rawDealer = context.createSocket(SocketType.DEALER);
        
        // Bind/connect our ZeroMQClient implementations
        router.bind();
        dealer.connect();
    }

    @AfterEach
    void tearDown() {
        // Close ZeroMQClient instances
        if (dealer != null) dealer.close();
        if (router != null) router.close();
        
        // Close raw sockets
        if (rawRouter != null) rawRouter.close();
        if (rawDealer != null) rawDealer.close();
        if (context != null) context.close();
    }

    @Test
    @Timeout(5)
    void testProperFrameHandling() throws InterruptedException {
        // Test that our ZeroMQClient properly handles frames by capturing and validating
        // the raw ZMQ messages that are exchanged
        
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // Start listening for messages
        router.listenForResponses(message -> {
            receivedMessage.set(message);
            messageLatch.countDown();
        });
        
        // Send a message from dealer to router
        dealer.sendRequest("Test message");
        
        // Wait for the router to receive the message
        assertTrue(messageLatch.await(2, TimeUnit.SECONDS), "Message should be received within timeout");
        
        // Verify the message format
        String received = receivedMessage.get();
        assertNotNull(received, "Message should not be null");
        
        // Format should be: identity\0\0message
        String[] frames = received.split("\0\0", 2);
        assertEquals(2, frames.length, "Message should have identity and content frames");
        assertFalse(frames[0].isEmpty(), "Identity frame should not be empty");
        assertEquals("Test message", frames[1], "Message content should match sent message");
    }

    @Test
    @Timeout(5)
    void testRouterReceivesCorrectFrames() throws InterruptedException {
        // Bind a raw router socket to a different port for direct testing
        String directEndpoint = "tcp://localhost:5563";
        rawRouter.bind(directEndpoint);
        
        // Create a dealer client that connects to this endpoint
        ZeroMQClient testDealer = new ZeroMQClient(directEndpoint, SocketType.DEALER);
        testDealer.connect();
        
        // Send a message from our dealer client
        testDealer.sendRequest("Frame test message");
        
        // Receive the message with the raw socket to check frames
        ZMQ.Poller poller = context.createPoller(1);
        poller.register(rawRouter, ZMQ.Poller.POLLIN);
        
        boolean messageReceived = false;
        byte[] identity = null;
        byte[] emptyFrame = null;
        byte[] content = null;
        
        // Poll with timeout
        if (poller.poll(2000) > 0) {
            if (poller.pollin(0)) {
                // First frame should be the identity
                identity = rawRouter.recv(0);
                
                // Second frame should be the empty delimiter
                emptyFrame = rawRouter.recv(0);
                
                // Third frame should be the content
                content = rawRouter.recv(0);
                
                messageReceived = true;
            }
        }
        
        // Clean up
        poller.close();
        testDealer.close();
        
        // Verify the frames
        assertTrue(messageReceived, "Raw router socket should receive the message");
        assertNotNull(identity, "Identity frame should not be null");
        assertNotNull(emptyFrame, "Empty frame should not be null");
        assertNotNull(content, "Content frame should not be null");
        
        assertEquals(0, emptyFrame.length, "Second frame should be empty");
        assertEquals("Frame test message", new String(content, ZMQ.CHARSET), "Content should match sent message");
    }

    @Test
    @Timeout(5)
    void testDealerReceivesCorrectFrames() throws InterruptedException {
        // Bind a raw dealer socket to a different port for direct testing
        String directEndpoint = "tcp://localhost:5564";
        rawDealer.setIdentity("TEST-DEALER-ID".getBytes(ZMQ.CHARSET));
        rawDealer.connect(directEndpoint);
        
        // Create a router client that binds to this endpoint
        ZeroMQClient testRouter = new ZeroMQClient(directEndpoint, SocketType.ROUTER);
        testRouter.bind();
        
        // Send a message from raw dealer to router
        rawDealer.sendMore("".getBytes(ZMQ.CHARSET)); // Empty delimiter
        rawDealer.send("Raw message test".getBytes(ZMQ.CHARSET), 0);
        
        // Set up a latch to wait for the message
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // Listen for the message with our router client
        testRouter.listenForResponses(message -> {
            receivedMessage.set(message);
            messageLatch.countDown();
        });
        
        // Wait for the message
        assertTrue(messageLatch.await(2, TimeUnit.SECONDS), "Router should receive the raw dealer message");
        
        // Verify the message format
        String received = receivedMessage.get();
        assertNotNull(received, "Received message should not be null");
        
        // Format should be: identity\0\0message
        String[] frames = received.split("\0\0", 2);
        assertEquals(2, frames.length, "Message should have identity and content frames");
        assertEquals("TEST-DEALER-ID", frames[0], "Identity should match the raw dealer identity");
        assertEquals("Raw message test", frames[1], "Content should match sent message");
        
        // Clean up
        testRouter.close();
    }

    @Test
    @Timeout(5)
    void testSendingToRouterWithProperFraming() throws InterruptedException {
        // This test verifies that our client can properly send messages to a raw ROUTER socket
        
        // Bind a raw router socket
        String directEndpoint = "tcp://localhost:5565";
        rawRouter.bind(directEndpoint);
        
        // Create our client dealer
        ZeroMQClient testDealer = new ZeroMQClient(directEndpoint, SocketType.DEALER);
        testDealer.connect();
        
        // Send a message
        testDealer.sendRequest("Properly framed message");
        
        // Set up poller to receive the message
        ZMQ.Poller poller = context.createPoller(1);
        poller.register(rawRouter, ZMQ.Poller.POLLIN);
        
        // Variables to store received frames
        byte[] identity = null;
        byte[] emptyFrame = null;
        byte[] content = null;
        
        // Poll with timeout
        if (poller.poll(2000) > 0) {
            if (poller.pollin(0)) {
                identity = rawRouter.recv(0);
                assertTrue(rawRouter.hasReceiveMore(), "Router should receive more frames after identity");
                
                emptyFrame = rawRouter.recv(0);
                assertTrue(rawRouter.hasReceiveMore(), "Router should receive more frames after empty frame");
                
                content = rawRouter.recv(0);
                assertFalse(rawRouter.hasReceiveMore(), "Router should not receive more frames after content");
            }
        }
        
        // Clean up
        poller.close();
        testDealer.close();
        
        // Verify the frames
        assertNotNull(identity, "Identity frame should not be null");
        assertNotNull(emptyFrame, "Empty frame should not be null");
        assertNotNull(content, "Content frame should not be null");
        
        assertEquals(0, emptyFrame.length, "Second frame should be empty");
        assertEquals("Properly framed message", new String(content, ZMQ.CHARSET), "Content should match sent message");
    }

    @Test
    @Timeout(5)
    void testFullMessageRoundtrip() throws InterruptedException {
        // Test a complete message roundtrip:
        // 1. Dealer sends to Router
        // 2. Router sends back to Dealer with proper framing
        
        CountDownLatch requestLatch = new CountDownLatch(1);
        CountDownLatch responseLatch = new CountDownLatch(1);
        
        AtomicReference<String> dealerMessage = new AtomicReference<>();
        AtomicReference<String> routerResponse = new AtomicReference<>();
        AtomicReference<String> dealerIdentity = new AtomicReference<>();
        
        // Set up router to receive messages and send response
        router.listenForResponses(message -> {
            // Router receives dealer message
            String[] frames = message.split("\0\0", 2);
            dealerIdentity.set(frames[0]);
            dealerMessage.set(frames[1]);
            requestLatch.countDown();
            
            // Router sends response to dealer
            String response = "Response to: " + frames[1];
            String framedResponse = frames[0] + "\0\0" + response;
            router.sendRequest(framedResponse);
        });
        
        // Set up dealer to receive router response
        dealer.listenForResponses(message -> {
            routerResponse.set(message);
            responseLatch.countDown();
        });
        
        // Send message from dealer to router
        dealer.sendRequest("Roundtrip test message");
        
        // Wait for roundtrip to complete
        assertTrue(requestLatch.await(2, TimeUnit.SECONDS), "Router should receive dealer message");
        assertTrue(responseLatch.await(2, TimeUnit.SECONDS), "Dealer should receive router response");
        
        // Verify the messages
        assertEquals("Roundtrip test message", dealerMessage.get(), "Router should receive correct dealer message");
        assertEquals("Response to: Roundtrip test message", routerResponse.get(), "Dealer should receive correct router response");
    }
} 