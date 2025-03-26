# Portfolio Assignment for the Lecture: Distributed Systems

**Dozent**: Prof. Dr. Michael Eichberg

**Version**: 2025-03-20 – Sales & Consulting

---

## Task Description

The aim of this task is to gain a better understanding of the implementation of distributed systems and business
processes within them. A system simulating long-running business processes using messages and SAGAs will be developed.
This also aims to improve understanding of concurrent programming.

You are to simulate a distributed system for booking hotels, focusing on technically relevant aspects.

## Requirements:

Implement a simple travel broker that enables customers to book trips consisting of several hotel bookings.

Total bookings should only succeed if all partial bookings are completed successfully.

If a hotel is unavailable, the entire booking is rolled back.

The system supports bookings with up to 5 hotels.

Consecutive bookings must not be in the same hotel, but a hotel can appear more than once (e.g., round trips).

Only bookings within the next 2 years are accepted (100 simplified weekly time blocks).

Each hotel has a fixed number of rooms and one room category.

Rooms are booked on a weekly basis (complete time blocks).

### Core Systems

1. Hotel Booking System

    - At least 3 instances at runtime (e.g., HolidayInTheMountains, TravelToTheSea, AwayFromHome).
    - Forwards booking requests to the travel broker and logs results:

        - "Everything booked"

        - "Hotel X could not be booked, reservations U, V, W cancelled"

        - Use meaningful timestamps to trace the system state.

2. Travel Broker

    - Single instance at runtime.
    - Coordinates all hotel bookings in a request.
    - May request bookings sequentially or in parallel.
    - Must handle technical/business failures gracefully.

3. Hotel Server
    - Each hotel or hotel group has a dedicated server.
    - Accepts/rejects reservations based on availability.

### Technical Requirements

-   Non-blocking systems: all communication must be asynchronous.

-   Use ZeroMQ for system communication.

-   Hotel data and booking process data should:

    -   Be available in suitable formats (properties, JSON, YAML, etc.)

    -   Or be generated at startup.

-   No permanent storage is needed.

-   Simulate:

    -   Failures

    -   Long latencies

    -   Rollback scenarios

-   Travel broker and booking-forwarding system are assumed reliable.

-   Hotel services may experience simulated technical and business errors.

### Requirements on the Simulation

Configurable parameters via file or runtime input:

-   Arrival rate of booking requests.

-   Average processing time of hotel services.

-   Probabilities:

    -   Message received but not processed (simulated crash).

    -   Message processed but not confirmed.

    -   Message successfully processed with possible outcomes:

        -   Booking successful

        -   Booking failed due to no availability (triggers rollback)

Use normal distributions for randomization.

### Documentation

Explicitly document:

-   Technical and business problems

-   Compensating transactions for each case

-   Coordinators and timing of compensation

-   Show that the system is eventually consistent

### General Requirements

-   Groups of five (individual marks possible).

-   Implementation in Java.

-   Allowed external libraries: only for logging, tracing, visualization, and ZeroMQ.

-   Keep the data model simple, and ensure booking failures occur.

Focus on:

-   Concurrency

-   Error handling

-   Transactional correctness

### Submission

Each group must submit:

-   Architecture and test concept document (3–5 pages)

-   Error cases document with system reactions

-   Java source code with comments and test data

-   Executable JAR files with initialization files and user manual

-   Demo video with voice commentary:

    -   Show functionality and error behavior

    -   Log/visualize system under load

    -   Keep video concise, and host it on a cloud platform (link must be valid for 8 weeks)

-   Task breakdown document with contributions per member

-   Honor declaration
