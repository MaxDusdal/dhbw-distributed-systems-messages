# Error Cases and Handling

### Best Case

```mermaid
sequenceDiagram
    participant BookingClient as Booking Service
    participant TravelBroker as Travel Broker
    participant Hotel1 as Hotel Server 1
    participant Hotel2 as Hotel Server 2

    BookingClient->>TravelBroker: submitBooking(tripRequest)
    activate TravelBroker

    TravelBroker->>Hotel1: bookRoom(timeBlock)
    TravelBroker->>Hotel2: bookRoom(timeBlock)
    activate Hotel1
    activate Hotel2

    Hotel1-->>TravelBroker: SUCCESS (timeBlock booked)
    Hotel2-->>TravelBroker: SUCCESS (timeBlock booked)
    deactivate Hotel1
    deactivate Hotel2

    TravelBroker->>BookingClient: CONFIRMED
    deactivate TravelBroker
```

### Worst Case

```mermaid
sequenceDiagram
    participant Client as Booking Service
    participant Broker as Travel Broker
    participant Hotel1 as Hotel Server 1
    participant Hotel2 as Hotel Server 2
    participant Hotel3 as Hotel Server 3
    participant Log as Compensation Log

    Client->>Broker: submitBooking(tripRequest)
    activate Broker

    Broker->>Hotel1: bookRoom(timeBlock)
    Broker->>Hotel2: bookRoom(timeBlock)
    Broker->>Hotel3: bookRoom(timeBlock)
    activate Hotel1
    activate Hotel2
    activate Hotel3

    Hotel1-->>Broker: SUCCESS
    Hotel2-->>Broker: FAIL (No rooms)
    deactivate Hotel2
    Note over Hotel3: Timeout - No response because of crash or delay
    deactivate Hotel3
		
		Broker->>Log: logCompensation(bookingId, Hotel1)
    Broker->>Log: logCompensation(bookingId, Hotel3)
    Broker->>Client: FAILED (Hotel2: No rooms, Hotel3: Timeout)
    deactivate Broker

    par Async Rollbacks via Compensation Log
        # Hotel1: Confirmed booking, retry until success
        loop Retry Hotel1 (exponential backoff)
            Log->>Hotel1: cancelBooking(timeBlock)
            activate Hotel1
            Hotel1-->>Log: FAIL (Technical error)
            deactivate Hotel1
        end
        Log->>Hotel1: cancelBooking(timeBlock)
        activate Hotel1
        Hotel1-->>Log: SUCCESS
        deactivate Hotel1

        # Hotel3: Ambiguous state, verify first
        Log->>Hotel3: checkBookingStatus(timeBlock)
        activate Hotel3
        alt Hotel3 processed the booking
            Hotel3-->>Log: CONFIRMED
            Log->>Hotel3: cancelBooking(timeBlock)
            Hotel3-->>Log: SUCCESS
        else Hotel3 never processed it
            Hotel3-->>Log: NOT_FOUND
            Note over Log: No action needed
        else Timeout again
            Hotel3-->>Log: TIMEOUT
            Note over Log: Retry check later
        end
        deactivate Hotel3
    end
```

[Exponential Backoff And Jitter | Amazon Web Services](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)

When one of the hotels explicitly replies with *FAIL*, the broker stops waiting for the remaining answers, declares the whole trip unsuccessful and writes a compensation entry for every hotel that had already confirmed. Those confirmations are then cancelled asynchronously; the compensation service keeps retrying the `cancelBooking` call with exponential back-off until each hotel acknowledges, so the system always winds up in a clean state.

If a hotel processes the booking but its reply is lost on the way back, the broker sees no answer at all. It treats the situation as “state unknown”, fails the trip and logs a probe task. The compensation service later asks that hotel for the booking’s status: if the room is indeed reserved it is cancelled exactly once, if not, nothing further happens. A plain timeout (where the hotel crashed or was too slow) follows the same path; the only difference is that the probe may have to wait until the server is back online.

Rollback traffic itself can fail, for example because a hotel is momentarily unreachable. In that case the compensation log simply schedules another attempt; retries continue with exponentially increasing intervals until the hotel finally responds or an operator intervenes. Because each cancel operation is idempotent, multiple deliveries are harmless.

Clients may resend the same trip request if their first attempt times-out. The broker recognises repeated `bookingId`s, ignores the duplicate work and returns the already computed outcome. This idempotence rules out double bookings even under flaky client connectivity.

With these mechanisms the platform guarantees eventual consistency: after transient failures or retries the final state will be either “all hotels booked” or “no hotel booked”, never an inconsistent mix.