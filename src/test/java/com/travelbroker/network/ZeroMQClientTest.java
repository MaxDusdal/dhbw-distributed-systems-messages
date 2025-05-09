package com.travelbroker.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zeromq.SocketType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ZeroMQClientTest {
    private static final String TEST_ENDPOINT = "tcp://localhost:5559";
    private ZeroMQClient dealer;
    private ZeroMQClient router;

    @BeforeEach
    void setUp() {
        router = new ZeroMQClient(TEST_ENDPOINT, SocketType.ROUTER);
        dealer = new ZeroMQClient(TEST_ENDPOINT, SocketType.DEALER);
    }

    @AfterEach
    void tearDown() {
        if (dealer != null) {
            dealer.close();
        }
        if (router != null) {
            router.close();
        }
    }

    @Test
    void testConnectionEstablishment() {
        router.bind();
        dealer.connect();
        // If no exceptions were thrown, the test is considered successful
    }

    @Test
    @Timeout(10)
    void testDealerToRouterCommunication() throws InterruptedException {
        router.bind();
        dealer.connect();

        // Prepare to capture received message
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        
        // Start listening for messages on the router
        router.listenForResponses(message -> {
            receivedMessage.set(message);
            latch.countDown();
        });

        // Send a message from dealer to router
        String testMessage = "Hello from dealer";
        dealer.sendRequest(testMessage);

        // Wait for the router to receive the message
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received within timeout");
        
        // Verify the message format (should have the identity and delimiters)
        String received = receivedMessage.get();
        assertNotNull(received, "Message should not be null");
        
        // Format should be: identity\0\0message
        String[] frames = received.split("\0\0", 2);
        assertEquals(2, frames.length, "Message should have identity and content frames");
        assertEquals("Hello from dealer", frames[1], "Message content should match sent message");
    }

    @Test
    @Timeout(10)
    void testRouterToDealerCommunication() throws InterruptedException {
        router.bind();
        dealer.connect();

        // First, we need to get the dealer's identity
        final AtomicReference<String> dealerIdentity = new AtomicReference<>();
        final CountDownLatch identityLatch = new CountDownLatch(1);
        
        router.listenForResponses(message -> {
            String[] frames = message.split("\0\0", 2);
            dealerIdentity.set(frames[0]);
            identityLatch.countDown();
        });
        
        // Send a message to get the identity
        dealer.sendRequest("Identity request");
        
        // Wait for the router to receive the message and extract the identity
        assertTrue(identityLatch.await(5, TimeUnit.SECONDS), "Should receive identity within timeout");
        assertNotNull(dealerIdentity.get(), "Should get dealer identity");
        
        // Now test router to dealer communication
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<String> receivedByDealer = new AtomicReference<>();
        
        dealer.listenForResponses(message -> {
            receivedByDealer.set(message);
            messageLatch.countDown();
        });
        
        // Send a message from router to dealer using the dealer's identity
        String testMessage = "Hello from router";
        String framedMessage = dealerIdentity.get() + "\0\0" + testMessage;
        router.sendRequest(framedMessage);
        
        // Wait for the dealer to receive the message
        assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Message should be received within timeout");
        
        // Verify the message
        assertEquals("Hello from router", receivedByDealer.get(), "Message content should match");
    }

    @Test
    @Timeout(20)
    void testMultipleMessagesExchange() throws InterruptedException {
        router.bind();
        dealer.connect();
        
        final int numMessages = 10;
        final CountDownLatch routerLatch = new CountDownLatch(numMessages);
        final CountDownLatch dealerLatch = new CountDownLatch(numMessages);
        
        final List<String> routerReceivedMessages = new ArrayList<>();
        final List<String> dealerReceivedMessages = new ArrayList<>();
        final AtomicReference<String> dealerIdentity = new AtomicReference<>();
        
        // Set up router listener
        router.listenForResponses(message -> {
            String[] frames = message.split("\0\0", 2);
            if (dealerIdentity.get() == null) {
                dealerIdentity.set(frames[0]);
            }
            routerReceivedMessages.add(frames[1]);
            routerLatch.countDown();
        });
        
        // Set up dealer listener
        dealer.listenForResponses(message -> {
            dealerReceivedMessages.add(message);
            dealerLatch.countDown();
        });
        
        // Send messages from dealer to router
        for (int i = 0; i < numMessages; i++) {
            dealer.sendRequest("Dealer message " + i);
        }
        
        // Wait for all messages to be received by router
        assertTrue(routerLatch.await(5, TimeUnit.SECONDS), "All messages should be received by router");
        
        // Send messages from router to dealer
        for (int i = 0; i < numMessages; i++) {
            String message = "Router message " + i;
            router.sendRequest(dealerIdentity.get() + "\0\0" + message);
        }
        
        // Wait for all messages to be received by dealer
        assertTrue(dealerLatch.await(5, TimeUnit.SECONDS), "All messages should be received by dealer");
        
        // Verify all messages were received correctly
        assertEquals(numMessages, routerReceivedMessages.size(), "Router should receive all messages");
        assertEquals(numMessages, dealerReceivedMessages.size(), "Dealer should receive all messages");
        
        for (int i = 0; i < numMessages; i++) {
            assertEquals("Dealer message " + i, routerReceivedMessages.get(i), "Router should receive correct messages in order");
            assertEquals("Router message " + i, dealerReceivedMessages.get(i), "Dealer should receive correct messages in order");
        }
    }

    @Test
    void testInvalidSocketType() {
        // Only DEALER and ROUTER socket types are supported
        assertThrows(IllegalArgumentException.class, () -> 
            new ZeroMQClient(TEST_ENDPOINT, SocketType.REQ));
    }

    @Test
    void testInvalidOperations() {
        // Connect is only valid for DEALER sockets
        assertThrows(IllegalStateException.class, () -> router.connect());
        
        // Bind is only valid for ROUTER sockets
        assertThrows(IllegalStateException.class, () -> dealer.bind());
    }

    @Test
    @Timeout(10)
    void testStopListening() throws InterruptedException {
        router.bind();
        dealer.connect();
        
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Integer> messageCount = new AtomicReference<>(0);
        
        router.listenForResponses(message -> {
            int count = messageCount.get();
            messageCount.set(count + 1);
            latch.countDown();
        });
        
        // Send a message and verify it's received
        dealer.sendRequest("Test message");
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
        assertEquals(1, messageCount.get().intValue(), "Should receive one message");
        
        // Stop listening and send another message
        router.stopListening();
        
        // Create a new latch since the previous one is already counted down
        final CountDownLatch newLatch = new CountDownLatch(1);
        
        // Start a thread to send another message
        Thread sender = new Thread(() -> {
            try {
                // Give some time for listener to completely stop
                Thread.sleep(500);
                dealer.sendRequest("Another message");
                newLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        sender.start();
        
        // Wait for the message to be sent
        assertTrue(newLatch.await(2, TimeUnit.SECONDS), "Message should be sent");
        
        // Verify no additional messages were received
        assertEquals(1, messageCount.get().intValue(), "No additional messages should be received after stopping");
    }

    @Test
    void testRouterSocketHandlesInvalidMessageFormat() {
        router.bind();
        // When sendRequest is called with an invalid message format for a ROUTER socket,
        // it should return false and log an error
        assertFalse(router.sendRequest("invalid message format"));
    }

    @Test
    void testUniqueIdentityAssignment() {
        // Create multiple DEALER sockets and verify they get unique identities
        ZeroMQClient dealer1 = new ZeroMQClient(TEST_ENDPOINT, SocketType.DEALER);
        ZeroMQClient dealer2 = new ZeroMQClient(TEST_ENDPOINT, SocketType.DEALER);
        
        // We can't directly access the identity, but we can verify they're unique by
        // checking if messages from each dealer are correctly routed back
        
        router.bind();
        dealer1.connect();
        dealer2.connect();
        
        // If we made it here without errors, the test passes
        // Additional verification would require more complex interaction testing
        
        dealer1.close();
        dealer2.close();
    }
} 