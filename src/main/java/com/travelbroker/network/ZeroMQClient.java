package com.travelbroker.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Client for asynchronous communication between system components using ZeroMQ.
 * Handles sending requests and listening for responses asynchronously.
 */
public class ZeroMQClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ZeroMQClient.class);
    
    private final ZContext context;
    private final String endpoint;
    private final SocketType socketType;
    private ZMQ.Socket socket;
    private ExecutorService listenerThread;
    private boolean isRunning = false;

    /**
     * Creates a new ZeroMQ client with the specified endpoint and socket type.
     *
     * @param endpoint   The ZeroMQ endpoint to connect to, for example "tcp://localhost:5555"
     * @param socketType The type of socket to use (DEALER or ROUTER)
     */
    public ZeroMQClient(String endpoint, SocketType socketType) {
        if (socketType != SocketType.DEALER && socketType != SocketType.ROUTER) {
            throw new IllegalArgumentException("Only DEALER or ROUTER socket types are supported");
        }
        this.context = new ZContext();
        this.endpoint = endpoint;
        this.socketType = socketType;
        this.socket = context.createSocket(socketType);
        
        // Set identity for DEALER sockets to ensure message correlation
        if (socketType == SocketType.DEALER) {
            socket.setIdentity(("CLIENT-" + java.util.UUID.randomUUID().toString()).getBytes(ZMQ.CHARSET));
        }
    }

    /**
     * Connects the client to its endpoint (for DEALER sockets).
     */
    public void connect() {
        if (socketType != SocketType.DEALER) {
            throw new IllegalStateException("Connect is only valid for DEALER sockets");
        }
        socket.connect(endpoint);
        logger.debug("DEALER socket connected to {}", endpoint);
    }

    /**
     * Binds the client to its endpoint (for ROUTER sockets).
     */
    public void bind() {
        if (socketType != SocketType.ROUTER) {
            throw new IllegalStateException("Bind is only valid for ROUTER sockets");
        }
        socket.bind(endpoint);
        logger.debug("ROUTER socket bound to {}", endpoint);
    }

    /**
     * Sends a message to the connected endpoint.
     *
     * @param message The message to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendRequest(String message) {
        try {
            if (socketType == SocketType.DEALER) {
                socket.sendMore("");
                return socket.send(message.getBytes(ZMQ.CHARSET), 0);
            } else if (socketType == SocketType.ROUTER) {
                String[] frames = message.split("\0\0", 2);
                if (frames.length >= 2) {
                    socket.sendMore(frames[0].getBytes(ZMQ.CHARSET));
                    socket.sendMore("");
                    return socket.send(frames[1].getBytes(ZMQ.CHARSET), 0);
                } else {
                    logger.error("Invalid message format for ROUTER socket: {}", message);
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Starts listening for responses asynchronously.
     *
     * @param callback The callback to invoke when a response is received
     */
    public void listenForResponses(Consumer<String> callback) {
        if (isRunning) {
            throw new IllegalStateException("Listener is already running");
        }
        
        isRunning = true;
        listenerThread = Executors.newSingleThreadExecutor();
        
        listenerThread.submit(() -> {
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    ZMQ.Poller poller = context.createPoller(1);
                    poller.register(socket, ZMQ.Poller.POLLIN);
                    
                    if (poller.poll(500) > 0) {
                        if (poller.pollin(0)) {
                            if (socketType == SocketType.ROUTER) {
                                byte[] identity = socket.recv(0);
                                if (identity == null) continue;
                                
                                byte[] empty = socket.recv(0);
                                if (empty == null) continue;
                                
                                byte[] message = socket.recv(0);
                                if (message == null) continue;
                                
                                String framedMessage = new String(identity, ZMQ.CHARSET) + 
                                                      "\0\0" + 
                                                      new String(message, ZMQ.CHARSET);
                                callback.accept(framedMessage);
                            } else if (socketType == SocketType.DEALER) {
                                byte[] empty = socket.recv(0);
                                if (empty == null) continue;
                                
                                byte[] message = socket.recv(0);
                                if (message == null) continue;
                                
                                // Just pass the message content for DEALER sockets
                                String messageContent = new String(message, ZMQ.CHARSET);
                                callback.accept(messageContent);
                            }
                        }
                    }
                    poller.close();
                } catch (Exception e) {
                    if (isRunning) {
                        logger.error("Error in listener thread: {}", e.getMessage(), e);
                    }
                }
            }
            logger.info("Message listener stopped for {}", endpoint);
        });
    }

    /**
     * Stops the response listener thread.
     */
    public void stopListening() {
        isRunning = false;
        if (listenerThread != null) {
            listenerThread.shutdown();
            try {
                if (!listenerThread.awaitTermination(2000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    listenerThread.shutdownNow();
                }
            } catch (InterruptedException e) {
                listenerThread.shutdownNow();
                Thread.currentThread().interrupt();
            }
            listenerThread = null;
        }
    }

    /**
     * Closes the socket and context, releasing any resources.
     */
    @Override
    public void close() {
        stopListening();
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (context != null) {
            context.close();
        }
        logger.info("ZeroMQ client closed for {}", endpoint);
    }
} 