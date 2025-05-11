package com.travelbroker.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigProvider {
    private static final String CONFIG_FILE = "config.properties";
    private static final Logger logger = LoggerFactory.getLogger(ConfigProvider.class);

    /**
     * Loads the configuration from the properties file.
     *
     * @return The loaded properties
     */
    public static Properties loadConfiguration() {
        Properties properties = new Properties();
        try (InputStream input = ConfigProvider.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.error("Unable to find {}", CONFIG_FILE);
                return properties;
            }

            properties.load(input);
        } catch (IOException e) {
            logger.error("Error loading configuration: {}", e.getMessage(), e);
        }
        return properties;
    }
}
