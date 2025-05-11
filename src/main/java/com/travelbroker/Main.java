package com.travelbroker;

import com.travelbroker.booking.BookingService;
import com.travelbroker.broker.TravelBroker;
import com.travelbroker.hotel.HotelServer;
import com.travelbroker.model.Hotel;
import com.travelbroker.util.ConfigProvider;
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
 * Command line arguments can be used to override configuration:
 * • java -jar travel-broker.jar BOOKING_SERVICES=3 HOTELS=5 BOOKING_REQUEST_ARRIVAL_RATE=20
 * • java -jar travel-broker.jar BOOKING_FAILURE_PROBABILITY=0.2
 * • Available parameters:
 *   - BOOKING_SERVICES: Number of booking service instances (default: 3)
 *   - HOTELS: Number of hotel servers (default: 5)
 *   - BOOKING_REQUEST_ARRIVAL_RATE: Rate of booking requests per minute (default: 10)
 *   - AVERAGE_PROCESSING_TIME: Average processing time in ms (default: 0)
 *   - BOOKING_FAILURE_PROBABILITY: Probability of booking failure (0.0-1.0, default: 0.0)
 *   - MESSAGE_LOSS_PROBABILITY: Probability of message loss (0.0-1.0, default: 0.0)
 * <p>
 * Press ENTER (or send CTRL-C) to stop everything gracefully.
 */
public final class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int DEFAULT_BOOKING_SERVICES = 3;
    private static final int DEFAULT_HOTEL_COUNT = 5;
    
    private static final String PARAM_BOOKING_SERVICES = "BOOKING_SERVICES";
    private static final String PARAM_HOTELS = "HOTELS";

    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static void main(String[] args) {
        // Load configuration with command line arguments
        Properties config = ConfigProvider.loadConfiguration(args);
        
        // Extract non-property configuration parameters
        int bookingServiceCount = getIntParam(config, PARAM_BOOKING_SERVICES, DEFAULT_BOOKING_SERVICES);
        int hotelCount = getIntParam(config, PARAM_HOTELS, DEFAULT_HOTEL_COUNT);
        
        List<Hotel> hotels = createHotels(hotelCount);

        try (TravelBroker broker = new TravelBroker("tcp://*:5555", "tcp://*:5556")) {
            broker.start(); 

            List<HotelServer> hotelServers = startHotelServers(hotels); // 2. hotels
            List<BookingService> bookingServices = startBookingServices(broker, bookingServiceCount, hotels); // 3. clients

            // Log the current configuration
            logConfiguration(config, bookingServiceCount, hotelCount);

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

    private static int getIntParam(Properties config, String key, int defaultValue) {
        String value = config.getProperty(key);
        if (value != null) {
            try {
                int val = Integer.parseInt(value);
                if (val > 0) {
                    return val;
                }
            } catch (NumberFormatException nfe) {
                logger.warn("Invalid value for {}: '{}', using default {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private static void logConfiguration(Properties config, int bookingServices, int hotels) {
        logger.info("System configuration:");
        logger.info("- Booking Services: {}", bookingServices);
        logger.info("- Hotels: {}", hotels);
        logger.info("- Booking Request Rate: {}/min", config.getProperty(ConfigProvider.BOOKING_REQUEST_ARRIVAL_RATE));
        logger.info("- Average Processing Time: {} ms", config.getProperty(ConfigProvider.AVERAGE_PROCESSING_TIME));
        logger.info("- Booking Failure Probability: {}", config.getProperty(ConfigProvider.BOOKING_FAILURE_PROBABILITY));
        logger.info("- Message Loss Probability: {}", config.getProperty(ConfigProvider.MESSAGE_LOSS_PROBABILITY));
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