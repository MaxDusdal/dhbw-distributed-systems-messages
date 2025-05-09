package com.travelbroker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utility class for JSON operations.
 * Provides methods to convert objects to JSON and vice versa.
 */
public class JsonUtil {
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    
    /**
     * Converts an object to JSON string.
     *
     * @param object The object to convert
     * @return The JSON string representation of the object
     */
    public static String toJson(Object object) {
        return gson.toJson(object);
    }
    
    /**
     * Converts a JSON string to an object of the specified class.
     *
     * @param json The JSON string to convert
     * @param classOfT The class of the object to create
     * @param <T> The type of the object to create
     * @return The object created from the JSON string
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
} 