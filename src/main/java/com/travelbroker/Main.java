package com.travelbroker;

import com.travelbroker.booking.BookingService;
import com.travelbroker.broker.TravelBroker;
import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Boots the whole distributed demo:
 * • TravelBroker (front-end :5555, back-end :5556)
 * • One HotelServer per hotel – each connects to tcp://localhost:5556
 * • N BookingService instances – each connects to tcp://localhost:5555
 * <p>
 * Press ENTER (or send CTRL-C) to stop everything gracefully.
 */
public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int DEFAULT_BOOKING_SERVICES = 3;
    private static final int DEFAULT_HOTEL_COUNT = 5;

    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static void main(String[] args) {

        int bookingServiceCount = parseInstanceCount(args, DEFAULT_BOOKING_SERVICES);
        List<Hotel> hotels = createHotels(DEFAULT_HOTEL_COUNT);

        try (TravelBroker broker = new TravelBroker("tcp://*:5555", "tcp://*:5556")) {
            broker.start(); 

            List<HotelServer> hotelServers = startHotelServers(hotels); // 2. hotels
            List<BookingService> bookingServices = startBookingServices(broker, bookingServiceCount, hotels); // 3.
                                                                                                              // clients

            // shutdown hook so Ctrl-C works as well
            Runtime.getRuntime().addShutdownHook(new Thread(LATCH::countDown));

            logger.info("System running – press ENTER to quit.");
            waitForEnter(); // block here

            bookingServices.forEach(Main::closeQuietly);
            hotelServers.forEach(Main::closeQuietly);
        } catch (Exception ex) {
            logger.error("Fatal error in Main", ex);
        }

        logger.info("System shutdown complete");
    }

    private static int parseInstanceCount(String[] args, int defaultValue) {
        if (args.length == 0)
            return defaultValue;
        try {
            int val = Integer.parseInt(args[0]);
            return val > 0 ? val : defaultValue;
        } catch (NumberFormatException nfe) {
            logger.warn("Invalid instance count argument '{}', using default {}", args[0], defaultValue);
            return defaultValue;
        }
    }

    private static List<Hotel> createHotels(int count) {
        List<Hotel> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String id = "H" + i;
            String name = "Hotel " + i;
            int rooms = 20 + i * 10; // 30, 40, …
            list.add(new Hotel(id, name, rooms));
            logger.info("Created hotel {} ({} rooms)", name, rooms);
        }
        return list;
    }

    private static List<HotelServer> startHotelServers(List<Hotel> hotels) {
        List<HotelServer> servers = new ArrayList<>(hotels.size());
        for (Hotel hotel : hotels) {
            String backendConnectAddress = TravelBroker.getBackendEndpoint();
            HotelServer server = new HotelServer(hotel);
            servers.add(server);
            logger.info("Started HotelServer for {} on {}", hotel.getId(), backendConnectAddress);
        }
        return servers;
    }

    private static List<BookingService> startBookingServices(TravelBroker broker,
            int instances,
            List<Hotel> hotels) {
        List<BookingService> list = new ArrayList<>(instances);
        for (int i = 0; i < instances; i++) {
            BookingService bs = new BookingService(broker, instances, hotels);
            bs.start();
            list.add(bs);
        }
        return list;
    }

    private static void waitForEnter() {
        new Thread(() -> {
            try (Scanner sc = new Scanner(System.in)) {
                sc.nextLine();
            } finally {
                LATCH.countDown();
            }
        }, "stdin-wait").start();

        try {
            LATCH.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception ex) {
            logger.error("Error closing {}", c.getClass().getSimpleName(), ex);
        }
    }
}