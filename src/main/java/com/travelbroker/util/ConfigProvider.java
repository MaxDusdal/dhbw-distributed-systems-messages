package com.travelbroker.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigProvider {
    private static final String CONFIG_FILE = "config.properties";
    private static final Logger logger = LoggerFactory.getLogger(ConfigProvider.class);
    
    // Available configuration parameters
    public static final String BOOKING_REQUEST_ARRIVAL_RATE = "BOOKING_REQUEST_ARRIVAL_RATE";
    public static final String AVERAGE_PROCESSING_TIME = "AVERAGE_PROCESSING_TIME";
    public static final String BOOKING_FAILURE_PROBABILITY = "BOOKING_FAILURE_PROBABILITY";
    public static final String MESSAGE_LOSS_PROBABILITY = "MESSAGE_LOSS_PROBABILITY";
    
    // Default values
    private static final String DEFAULT_BOOKING_REQUEST_ARRIVAL_RATE = "10";
    private static final String DEFAULT_AVERAGE_PROCESSING_TIME = "100";
    private static final String DEFAULT_BOOKING_FAILURE_PROBABILITY = "0.05";
    private static final String DEFAULT_MESSAGE_LOSS_PROBABILITY = "0.05";
    
    // Global configuration settings
    private static Properties properties;

    /**
     * Loads the configuration from the properties file and then overrides with command line arguments.
     *
     * @param args Command line arguments in the format of key=value
     * @return The loaded properties with command line overrides
     */
    public static Properties loadConfiguration(String[] args) {
        // Start with file-based properties
        properties = loadPropertiesFromFile();
        
        // Set default values if not present
        setDefaultIfMissing(BOOKING_REQUEST_ARRIVAL_RATE, DEFAULT_BOOKING_REQUEST_ARRIVAL_RATE);
        setDefaultIfMissing(AVERAGE_PROCESSING_TIME, DEFAULT_AVERAGE_PROCESSING_TIME);
        setDefaultIfMissing(BOOKING_FAILURE_PROBABILITY, DEFAULT_BOOKING_FAILURE_PROBABILITY);
        setDefaultIfMissing(MESSAGE_LOSS_PROBABILITY, DEFAULT_MESSAGE_LOSS_PROBABILITY);
        
        // Override with command line arguments
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.contains("=")) {
                    String[] parts = arg.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim().toUpperCase();
                        String value = parts[1].trim();
                        properties.setProperty(key, value);
                        logger.info("Override configuration: {}={}", key, value);
                    }
                }
            }
        }
        
        return properties;
    }
    
    /**
     * Get the current configuration properties
     * @return The current properties
     */
    public static Properties getConfiguration() {
        if (properties == null) {
            properties = loadPropertiesFromFile();
        }
        return properties;
    }
    
    private static void setDefaultIfMissing(String key, String defaultValue) {
        if (!properties.containsKey(key)) {
            properties.setProperty(key, defaultValue);
        }
    }
    
    /**
     * Loads properties from the default config file
     */
    private static Properties loadPropertiesFromFile() {
        Properties props = new Properties();
        try (InputStream input = ConfigProvider.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.warn("Unable to find {}. Using default values.", CONFIG_FILE);
                return props;
            }

            props.load(input);
            logger.info("Loaded configuration from {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Error loading configuration: {}", e.getMessage(), e);
        }
        return props;
    }
}
