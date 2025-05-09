package com.travelbroker.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a customer in the system
 */
public class Customer implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID customerId;

    public Customer() {
        this.customerId = UUID.randomUUID();
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }
}