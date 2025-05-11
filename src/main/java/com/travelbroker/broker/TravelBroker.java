package com.travelbroker.broker;

import com.travelbroker.dto.BookingRequest;
import com.travelbroker.dto.BookingResponse;
import com.travelbroker.dto.HotelRequest;
import com.travelbroker.dto.HotelResponse;
import com.travelbroker.model.BookingStatus;
import com.travelbroker.model.HotelAction;
import com.travelbroker.network.ZeroMQClient;
import com.travelbroker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;

import java.util.*;
import java.util.concurrent.*;

/**
 * TravelBroker: accepts trip-booking requests from BookingServices (front-end)
 * and forwards individual hotel requests to the corresponding HotelServer
 * instance (back-end). One HotelServer process == one hotel.
 */
public final class TravelBroker implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TravelBroker.class);

    private static final String CLIENT_ENDPOINT = "tcp://localhost:5555"; // specific address for booking services
    private static final String WORKER_ENDPOINT = "tcp://localhost:5556"; // specific address for hotel servers
    private static final long BOOKING_TIMEOUT_MS = 30_000;
    private static final long TIMEOUT_SWEEP_MS = 5_000;
    // Worker (hotel) registration
    private final Map<String, String> hotelToWorker = new ConcurrentHashMap<>(); // TODO: Change data type to UUID
    private final Map<String, String> workerToHotel = new ConcurrentHashMap<>(); // inverse map for fast lookup
    /**
     * bookingId → aggregate state
     */
    private final Map<UUID, PendingBooking> pendingBookings = new ConcurrentHashMap<>();
    private final ExecutorService processor = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService timeoutSweep = Executors.newSingleThreadScheduledExecutor();
    private String frontendEndpoint = "tcp://*:5555"; // client side
    private final ZeroMQClient frontEnd = new ZeroMQClient(frontendEndpoint, SocketType.ROUTER);
    private String backendEndpoint = "tcp://*:5556"; // hotel side
    private final ZeroMQClient backEnd = new ZeroMQClient(backendEndpoint, SocketType.ROUTER);
    private volatile boolean running;

    public TravelBroker(String frontendEndpoint, String backendEndpoint) {
        this.frontendEndpoint = frontendEndpoint;
        this.backendEndpoint = backendEndpoint;
    }

    public static String getClientEndpoint() {
        return CLIENT_ENDPOINT;
    }

    public static String getBackendEndpoint() {
        return WORKER_ENDPOINT;
    }

    private static void shutdown(ExecutorService ex) {
        ex.shutdown();
        try {
            ex.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            ex.shutdownNow();
        }
    }

    public void start() {
        if (running)
            return;
        running = true;

        frontEnd.bind();
        backEnd.bind();

        frontEnd.listenForResponses(this::onFrontEndMessage);
        backEnd.listenForResponses(this::onBackEndMessage);

        timeoutSweep.scheduleAtFixedRate(this::sweepForTimeouts,
                TIMEOUT_SWEEP_MS,
                TIMEOUT_SWEEP_MS,
                TimeUnit.MILLISECONDS);

        logger.info("TravelBroker ready — frontEnd {}  backEnd {}", frontendEndpoint, backendEndpoint);
    }

    @Override
    public void close() {
        if (!running)
            return;
        running = false;
        shutdown(processor);
        shutdown(timeoutSweep);
        frontEnd.close();
        backEnd.close();
        logger.info("TravelBroker stopped");
    }

    // -----------------------------------------------------------------
    // front-end (BookingService → broker)
    // -----------------------------------------------------------------
    private void onFrontEndMessage(String frame) {
        // frame = clientId \0\0 json
        String[] parts = frame.split("\0\0", 2);
        if (parts.length != 2) {
            logger.error("Malformed client frame {}", frame);
            return;
        }
        String clientId = parts[0];
        BookingRequest req = JsonUtil.fromJson(parts[1], BookingRequest.class);
        if (req == null || req.getBookingId() == null) {
            logger.error("Invalid booking payload {}", parts[1]);
            return;
        }
        processor.submit(() -> dispatchBooking(clientId, req));
    }

    private void dispatchBooking(String clientId, BookingRequest request) {
        UUID bookingId = request.getBookingId();
        PendingBooking pendingBooking = new PendingBooking(clientId, request);
        pendingBookings.put(bookingId, pendingBooking);
        pendingBooking.setStatus(BookingStatus.PROCESSING);

        boolean unassignedWorkerId = Arrays.stream(request.getHotelRequests())
                .anyMatch(hr -> hotelToWorker.get(hr.getBooking().getHotelId()) == null);

        if (unassignedWorkerId) {
            logger.error("No HotelServer registered for one or more hotels");
            pendingBooking.setStatus(BookingStatus.FAILED);
            replyToClient(pendingBooking, "No HotelServer registered for one or more hotels");
            return;
        }

        for (HotelRequest hr : request.getHotelRequests()) {
            String hotelId = hr.getBooking().getHotelId();
            String workerId = hotelToWorker.get(hotelId);
            pendingBooking.registerRequest(hr); // Tracks request using unique id
            String payload = JsonUtil.toJson(hr);
            String message = workerId + "\0\0" + payload; // ROUTER envelope
            boolean sent = backEnd.sendRequest(message);
            if (!sent) {
                logger.error("Failed to send request to hotel {} via worker {}", hotelId, workerId);
            } else {
                logger.info("Sent request to hotel {} via worker {}", hotelId, workerId);
            }
        }
    }

    private void onBackEndMessage(String frame) {
        logger.error(frame);
        // frame = workerId \0\0 payload
        String[] parts = frame.split("\0\0", 2);
        if (parts.length != 2) {
            logger.warn("Malformed worker frame {}", frame);
            return;
        }
        String workerId = parts[0];
        String payload = parts[1];

        // registration handshake: "READY:<hotelId>"
        if (payload.startsWith("READY:")) {
            String hotelId = payload.substring("READY:".length());
            hotelToWorker.put(hotelId, workerId);
            workerToHotel.put(workerId, hotelId);
            logger.debug("Registered HotelServer {} → workerId {}", hotelId, workerId);
            return;
        }

        // normal HotelResponse
        HotelResponse hotelResponse = JsonUtil.fromJson(payload, HotelResponse.class);
        if (hotelResponse == null || hotelResponse.getRequestId() == null) {
            logger.error("Invalid HotelResponse {}", payload);
            return;
        }

        String hotelId = workerToHotel.get(workerId);
        if (hotelId == null) {
            logger.warn("Worker {} not registered, ignoring response", workerId);
            return;
        }

        UUID bookingId = findBookingIdByRequestId(hotelResponse.getRequestId());

        if (bookingId == null) {
            logger.warn("Orphan response for request {}", hotelResponse.getRequestId());
            return;
        }

        PendingBooking pendingBooking = pendingBookings.get(bookingId);
        if (pendingBooking == null) {
            logger.warn("Booking {} was found by request ID but is no longer in pendingBookings", bookingId);
            return;
        }

        // Record the result
        pendingBooking.recordBookingResult(hotelResponse.getRequestId(), hotelResponse.isSuccess());

        // Check if all hotels have responded
        if (pendingBooking.allBookingsAnswered()) {
            logger.info("All hotels have responded for booking {}", bookingId);
            finalizeBooking(pendingBooking);
        }
    }

    private void finalizeBooking(PendingBooking pb) {
        if (!pb.allBookingsSuccessful()) {
            initiateRollback(pb);
            return;
        }
        pb.setStatus(BookingStatus.CONFIRMED);
        replyToClient(pb, "All hotel bookings confirmed");
        pendingBookings.remove(pb.bookingId);
    }

    private void sweepForTimeouts() {
        long now = System.currentTimeMillis();
        for (PendingBooking pb : List.copyOf(pendingBookings.values())) {
            if (now - pb.createdAt > BOOKING_TIMEOUT_MS && !pb.allBookingsAnswered()) {
                initiateRollback(pb);
            }
        }
    }

    private void initiateRollback(PendingBooking pb) {
        pb.setStatus(BookingStatus.FAILED);
        replyToClient(pb, "Timeout waiting for hotel replies");
        pb.setStatus(BookingStatus.ROLLING_BACK);
        rollbackHotelBookings(pb);
    }

    private void rollbackHotelBookings(PendingBooking pb) {
        int CANCELLATION_RETRIES = 5;

        for (int i = 0; i < CANCELLATION_RETRIES; i++) {
            cancelHotelBookings(pb);
            try {
                long BACKOFF_BASE_MS = 1000;
                int sleepMs = (int) Math.min(BOOKING_TIMEOUT_MS, BACKOFF_BASE_MS * Math.pow(2, i + 1));
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (pb.allBookingsSuccessful()) {
                pb.setStatus(BookingStatus.ROLLBACK_SUCCESS);
                replyToClient(pb, "All hotel bookings rolled back successfully");
                pendingBookings.remove(pb.bookingId);
                return;
            }
        }
        pb.setStatus(BookingStatus.ROLLBACK_TIMEOUT);
        replyToClient(pb, "Failed to roll back all hotel bookings");
        pendingBookings.remove(pb.bookingId);
    }

    private void cancelHotelBookings(PendingBooking pb) {
        pb.removeConsistentBookings();

        for (HotelRequest hr : pb.hotelRequests.values()) {
            String hotelId = hr.getBooking().getHotelId();
            String workerId = hotelToWorker.get(hotelId);
            hr.setAction(HotelAction.CANCEL);
            hr.successful = false;
            hr.answered = false;
            String payload = JsonUtil.toJson(hr);
            String message = workerId + "\0\0" + payload;
            backEnd.sendRequest(message);
        }
    }

    private void replyToClient(PendingBooking pb, String msg) {
        BookingResponse resp = new BookingResponse(pb.bookingId, pb.status.name(), msg);
        String frame = pb.clientId + "\0\0" + JsonUtil.toJson(resp);
        frontEnd.sendRequest(frame);
        logger.debug("Sent {} to client {} (booking {})",
                pb.status, pb.clientId, pb.bookingId);
    }

    private UUID findBookingIdByRequestId(UUID requestId) {
        return pendingBookings.entrySet().stream()
                .filter(entry -> entry.getValue().getRequestResults().containsKey(requestId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static final class PendingBooking {
        private final String clientId;
        private final UUID bookingId;
        private final long createdAt = System.currentTimeMillis();
        private final Map<UUID, HotelRequest> hotelRequests = new HashMap<>();
        private volatile BookingStatus status = BookingStatus.PENDING;

        PendingBooking(String clientId, BookingRequest req) {
            this.clientId = clientId;
            this.bookingId = req.getBookingId();
        }

        void registerRequest(HotelRequest hr) {
            hotelRequests.put(hr.getRequestID(), hr);
        }

        void recordBookingResult(UUID requestId, boolean ok) {
            HotelRequest hr = hotelRequests.get(requestId);
            hr.successful = ok;
            hr.answered = true;
        }

        boolean allBookingsAnswered() {
            return hotelRequests.values().stream().allMatch(hr -> hr.answered);
        }

        boolean allBookingsSuccessful() {
            return hotelRequests.values().stream().allMatch(hr -> hr.successful);
        }

        void setStatus(BookingStatus st) {
            this.status = st;
        }

        Map<UUID, HotelRequest> getRequestResults() {
            return hotelRequests;
        }

        void removeConsistentBookings() {
            hotelRequests.entrySet().removeIf(entry -> entry.getValue().answered && entry.getValue().successful);
        }
    }
}
