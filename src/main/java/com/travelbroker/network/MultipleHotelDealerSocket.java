package com.travelbroker.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Thread-sichere Implementierung für die Kommunikation mit mehreren Endpunkten über ZeroMQ.
 * Nutzt intern die ZeroMQClient-Klasse für die Socket-Verwaltung.
 */
public class MultipleHotelDealerSocket implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MultipleHotelDealerSocket.class);

    private final Map<String, ZeroMQClient> endpointClients = new ConcurrentHashMap<>();
    private final BlockingQueue<MessageData> sendQueue = new LinkedBlockingQueue<>();
    private final ExecutorService senderExecutor;
    private volatile boolean running = false;

    // Callback für empfangene Nachrichten
    private BiConsumer<String, String> messageHandler;

    /**
     * Erstellt eine neue ThreadSafeMultiDealerSocket-Instanz.
     */
    public MultipleHotelDealerSocket() {
        this.senderExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Setzt den Handler für eingehende Nachrichten.
     * @param handler Callback-Funktion für (endpointName, messageContent)
     */
    public void setMessageHandler(BiConsumer<String, String> handler) {
        this.messageHandler = handler;

        // Handler für existierende Clients aktualisieren
        for (Map.Entry<String, ZeroMQClient> entry : endpointClients.entrySet()) {
            final String endpointName = entry.getKey();
            entry.getValue().listenForResponses(message -> {
                if (messageHandler != null) {
                    messageHandler.accept(endpointName, message);
                }
            });
        }
    }

    /**
     * Registriert einen neuen Endpunkt.
     *
     * @param endpointName Name des Endpunkts (z.B. "hotel1")
     * @param address Die Zieladresse (z.B. "tcp://localhost:5555")
     * @return true wenn der Endpunkt neu hinzugefügt wurde, false wenn er bereits existierte
     */
    public boolean addEndpoint(String endpointName, String address) {
        if (endpointClients.containsKey(endpointName)) {
            return false;
        }

        ZeroMQClient client = new ZeroMQClient(address, SocketType.DEALER);
        client.connect();

        // Listener für eingehende Nachrichten einrichten
        if (messageHandler != null) {
            final String epName = endpointName; // Final copy for lambda
            client.listenForResponses(message -> messageHandler.accept(epName, message));
        }

        endpointClients.put(endpointName, client);
        logger.info("Connected to endpoint: {} at {}", endpointName, address);
        return true;
    }

    /**
     * Fügt mehrere Endpunkte auf einmal hinzu.
     *
     * @param endpointMap Map von Endpunktnamen zu Adressen
     */
    public void addEndpoints(Map<String, String> endpointMap) {
        for (Map.Entry<String, String> entry : endpointMap.entrySet()) {
            addEndpoint(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Prüft, ob ein Endpunkt mit dem angegebenen Namen existiert.
     *
     * @param endpointName Name des Endpunkts
     * @return true wenn der Endpunkt existiert, sonst false
     */
    public boolean hasEndpoint(String endpointName) {
        return endpointClients.containsKey(endpointName);
    }

    /**
     * Startet den Sender-Thread.
     */
    public void start() {
        running = true;
        senderExecutor.submit(this::processSendQueue);
        logger.info("Thread-safe multi-dealer socket started with {} endpoints",
                endpointClients.size());
    }

    /**
     * Sendet eine Nachricht an einen bestimmten Endpunkt.
     * Die Nachricht wird in eine Queue eingereiht und asynchron gesendet.
     *
     * @param endpointName Name des Ziel-Endpunkts
     * @param message Die zu sendende Nachricht (String)
     * @throws IllegalArgumentException wenn der Endpunkt nicht existiert
     * @throws InterruptedException wenn der Thread während des Wartens unterbrochen wird
     */
    public void sendMessage(String endpointName, String message)
            throws IllegalArgumentException, InterruptedException {

        if (!endpointClients.containsKey(endpointName)) {
            throw new IllegalArgumentException("Unknown endpoint: " + endpointName);
        }

        sendQueue.put(new MessageData(endpointName, message));
    }

    /**
     * Worker-Methode, die kontinuierlich Nachrichten aus der Queue verarbeitet.
     */
    private void processSendQueue() {
        while (running) {
            try {
                MessageData data = sendQueue.take();

                ZeroMQClient client = endpointClients.get(data.endpointName);
                if (client != null) {
                    // Nachricht mit dem entsprechenden Client senden
                    boolean success = client.sendRequest(data.message);
                    if (success) {
                        logger.debug("Sent message to endpoint: {}", data.endpointName);
                    } else {
                        logger.error("Failed to send message to endpoint: {}", data.endpointName);
                    }
                } else {
                    logger.error("Endpoint not found: {}", data.endpointName);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Sender thread interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("Error sending message", e);
            }
        }
        logger.info("Message sender thread stopped");
    }

    /**
     * Stoppt den Sender-Thread und schließt alle Client-Verbindungen.
     */
    public void stop() {
        running = false;

        senderExecutor.shutdownNow();
        try {
            if (!senderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Sender executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Alle Clients schließen
        for (Map.Entry<String, ZeroMQClient> entry : endpointClients.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                logger.error("Error closing client for endpoint: {}", entry.getKey(), e);
            }
        }
        endpointClients.clear();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Hilfsdatenstruktur für die Nachrichten-Queue.
     */
    private static class MessageData {
        final String endpointName;
        final String message;

        MessageData(String endpointName, String message) {
            this.endpointName = endpointName;
            this.message = message;
        }
    }
}