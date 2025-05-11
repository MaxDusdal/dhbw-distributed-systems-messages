# Travel Broker System

This is a distributed system demonstration that implements the SAGA pattern for coordinating travel bookings across multiple hotel services. The system consists of:

- **TravelBroker**: The central orchestrator that coordinates booking requests
- **HotelServer**: Independent services that manage hotel room inventory (one per hotel)
- **BookingService**: Client applications that generate booking requests

## Architecture

The system demonstrates a distributed transaction pattern (SAGA) to ensure either all hotels in a trip are booked successfully, or all bookings are rolled back if any fail. Communication between components happens asynchronously via ZeroMQ messaging.

## Running the Application

### Using JAR File

```bash
# Run with default settings
java -jar travel-broker.jar

# Run with custom parameters
java -jar travel-broker.jar BOOKING_SERVICES=4 HOTELS=7 BOOKING_REQUEST_ARRIVAL_RATE=30
```

### Using Maven

```bash
# Build the application
mvn clean package

# Run with Maven
mvn exec:java
```

## Command Line Parameters

You can override default configuration values using command line parameters in the format `KEY=VALUE`:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `BOOKING_SERVICES` | Number of booking service instances | 3 |
| `HOTELS` | Number of hotel servers | 5 |
| `BOOKING_REQUEST_ARRIVAL_RATE` | Rate of booking requests per minute | 10 |
| `AVERAGE_PROCESSING_TIME` | Average processing time in ms | 0 |
| `BOOKING_FAILURE_PROBABILITY` | Probability of booking failure (0.0-1.0) | 0.0 |
| `MESSAGE_LOSS_PROBABILITY` | Probability of message loss (0.0-1.0) | 0.0 |

### Examples

```bash
# Increase load by having more booking services and a higher request rate
java -jar travel-broker.jar BOOKING_SERVICES=5 BOOKING_REQUEST_ARRIVAL_RATE=60

# Test error scenarios with artificial failures
java -jar travel-broker.jar BOOKING_FAILURE_PROBABILITY=0.2 MESSAGE_LOSS_PROBABILITY=0.1

# Simulate slower processing
java -jar travel-broker.jar AVERAGE_PROCESSING_TIME=250
```

## System Behavior

- Each booking service generates random trip bookings consisting of 1-5 hotel stays
- The travel broker orchestrates the booking process, ensuring transactional integrity
- If any hotel booking fails, the broker initiates compensation (rollback) for all successful bookings
- The system handles network failures gracefully with retry mechanisms
- Statistics about bookings are displayed when the system is stopped

## Implementation Details

- ZeroMQ is used for asynchronous communication between components
- The SAGA pattern is implemented for distributed transaction management
- Java Records are used for immutable data transfer objects
- Concurrent data structures ensure thread safety
- Logging provides visibility into system operation

## Stopping the Application

The application can be stopped by:
- Pressing the ENTER key in the console
- Using CTRL+C (graceful shutdown)