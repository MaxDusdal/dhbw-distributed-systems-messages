package com.travelbroker.model;

import java.io.Serializable;
import java.util.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Customer customer = (Customer) o;
        return Objects.equals(customerId, customer.customerId);
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                '}';
    }
}