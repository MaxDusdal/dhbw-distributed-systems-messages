# High-Level Architecture

# Systems

## Booking Service

→ Client system, 3 reliable instances at runtime

- Generates booking requests
    - Configuration: Request dispatch rate
        
        = Travel Broker Request Arrival Rate (delay between travel bookings) * booking service count (3)
        
- Logs results as human-readable messages
    - if failure: display detailed cause and compensation action

## Travel Broker

→ Singleton, reliable central orchestrator

- Receives trip bookings from the booking services
    - no request validation, correct format expected
- Sends booking requests to the hotel servers in parallel
    - all-or-nothing commitment
    - compensation in case of any failure
- Coordinates the [SAGA pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/saga) for trip bookings
    - Handles compensations (rollbacks) and retries
    - Does only store data during the life cycle of a booking request and compensations
        
        → no persistent storage
        
- Manages booking state transitions
- Responds to booking service with either success or failure + details message

### States

| Status | Meaning |
| --- | --- |
| `PENDING` | Booking request received, awaiting processing (e.g. because of message queuing or concurrency limits). |
| `PROCESSING` | Actively coordinating hotel bookings. |
| `CONFIRMED` | All hotel bookings succeeded. |
| `ROLLING_BACK` | Compensating for failed bookings (rollbacks in progress). |
| `FAILED` | Final failure state, compensations attempted. |

```mermaid
stateDiagram-v2
    [*] --> PENDING : Booking received
		PENDING --> PROCESSING : Begin processing
		state processing_results <<choice>>
		PROCESSING --> processing_results
		processing_results --> CONFIRMED : All hotels booked
		processing_results --> ROLLING_BACK : Any booking failure
		ROLLING_BACK --> FAILED : Compensations initiated
		CONFIRMED --> [*] : Success
		FAILED --> [*] : Failure

```

- All failure compensations are tracked internally. Failures are logged but not represented as separate states for simplicity.
- Optimistically transitioning to the failed state directly after initiating the rollbacks avoids complex timeout logic. The travel broker relies on eventual consistency and retries for pending compensations even after sending a failure response.

## Hotel Server

→ 1 unreliable instance per hotel, 5 hotels at runtime

- Receives bookings from travel broker
    - no request validation, correct format expected
- Handles hotel-specific bookings and rollbacks
- Simulates technical/business failures and delays
- Uses thread-safe inventory (e.g. ConcurrentHashMap)

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> PROCESSING : Booking Request Received
    
    state PROCESSING {
		    state rooms_available <<choice>>
        [*] --> rooms_available
        rooms_available --> SUCCESS : Rooms Available
        rooms_available --> NO_AVAILABILITY : No Rooms Available
        SUCCESS --> [*]
        NO_AVAILABILITY --> [*]
    }

    PROCESSING --> IDLE
```

### Simulation Configuration

- Parameters should be variable in program or adjustable via configuration file
    - Average processing time of inquiries
    - Acceptance rate without processing (likelihood of not going from idle to processing)
    - Processing rate without confirmation (likelihood of not sending a response after booking internally)
- Random Numbers are generated based on normal distributions
- The test data should trigger “no availability” business failures at least occasionally

# Technical Implementation

## Data Model

```mermaid
erDiagram
    CUSTOMER ||--o{ TRIP_BOOKING: "makes"
    TRIP_BOOKING ||--o{ HOTEL_BOOKING: "contains"
    HOTEL_BOOKING }o--|| HOTEL: "books"

    CUSTOMER {
        string id PK
    }

    TRIP_BOOKING {
        string id PK
        string customerId FK
        string status "PENDING/CONFIRMED/FAILED"
    }

    HOTEL_BOOKING {
        string id PK
        string tripId FK
        string hotelId FK
        int timeBlock "0-99 (week number)"
    }

    HOTEL {
        string hotelId PK
        string name
        int totalRooms "Fixed capacity"
        string category
    }
```

- Assumed simplifications
    - Each customer books a whole room, so no room entity and guest capacity checks are needed.
    - Each hotel booking spans exactly one week, which avoid overlapping availability checks.

## Core Classes

```mermaid
classDiagram
direction LR
		class BookingService {
        - ZeroMQClient mqClient
        - Logger logger
        + submitBooking(BookingRequest request)
        + receiveConfirmation(String response)
    }
    
    class TravelBroker {
        - List<HotelService> hotelServices
        - BookingStateManager stateManager
        - ZeroMQClient mqClient
        - CompensationLog compensationLog
        + processBooking(BookingRequest request)
        + handleRollback(String bookingId)
    }

    class HotelServer {
        - Hotel hotel
        - Set<Integer> bookedTimeBlocks
        + bookRoom(int timeBlock) boolean
        + cancelBooking(int timeBlock) boolean
    }

    class BookingRequest {
        - String bookingId
        - String customerId
        - List<String> hotelIds
        - int startTimeBlock
        + toJSON() String
    }

    class BookingStateManager {
        - Map<String, BookingState> states
        + updateState(String bookingId, BookingState state)
        + getCurrentState(String bookingId) BookingState
    }

    class ZeroMQClient {
        - ZContext context
        - ZMQ.Socket socket
        + sendRequest(String message)
        + listenForResponses(Consumer<String> callback)
    }

    class CompensationLog {
        - List<CompensationEntry> compensations
        + logCompensation(String bookingId, String hotelId, int timeBlock)
        + retryFailedRollbacks()
    }

    class Hotel {
        - String id
        - String name
        - int totalRooms
    }
		
		BookingService "3" --> "1..*" BookingRequest : dispatches
    BookingService "1" *-- "1" ZeroMQClient : uses
    TravelBroker "1" *-- "1" BookingStateManager : manages
    TravelBroker "1" *-- "1" ZeroMQClient : uses
    TravelBroker "1" *-- "1" CompensationLog : tracks
    TravelBroker "1" *-- "5" HotelServer : orchestrates
    HotelServer "1" *-- "1" Hotel : references
    TravelBroker "1" --> "1..*" BookingRequest : processes
```

**Further classes and functions may be added as needed, because this design just drafts the core functionalities.** 

### Design Decisions

1. **TravelBroker**
    
    **→** Orchestrator
    
    - Aggregates multiple hotel servers (one per hotel) to parallelize bookings.
    - **Uses `ZeroMQClient`** as a dedicated class for async messaging, which ensures loose coupling.
    - By relying on a `BookingStateManager`, the state tracking (PENDING, PROCESSING, etc.) is centralized for robustness.
2. **Booking Service**
    
    → Frontend client
    
    - Initiates booking requests and logs results.
3. **HotelServer**
    
    **→** Encapsulates business logic
    
    - References `Hotel` and directly checks availability via `totalRooms` and `bookedTimeBlocks`.
4. **BookingRequest**
    
    **→** Data Transfer Object (DTO
    
    - Serializability to JSON facilitates ZeroMQ messaging.
5. **CompensationLog**
    
    **→** for eventual consistency
    
    - The tracking of failed rollbacks enables retries without blocking the main process.
6. **ZeroMQClient**
    
    **→** for asynchronous communication
    
    - Decouples networking logic to promote scalability and testability.

## Booking Sequence

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

## Libraries

- [**JeroMQ**](https://github.com/zeromq/jeromq) for asynchronous network communication between systems
    
    → [ZeroMQ Messaging Patterns](https://zguide.zeromq.org/docs/chapter2/#Messaging-Patterns)
    
- ([OpenTelemetry](https://opentelemetry.io/docs/languages/java/) for tracing)
    - Possible custom implementation
        
        ```java
        public class TraceLogger {
            public static synchronized void log(String bookingId, String component, String event, String status) {
                long timestamp = System.currentTimeMillis();
                System.out.printf("[%d] [%s] [%s] %s - %s%n", 
                                  timestamp, bookingId, component, event, status);
            }
        }
        
        /* Sample output
        [1712758432350] [BK1234] [TravelBroker] Booking started - INIT
        [1712758432392] [BK1234] [HotelServiceA] Request received - PENDING
        [1712758432440] [BK1234] [HotelServiceA] Response sent - SUCCESS
        [1712758432500] [BK1234] [TravelBroker] Booking completed - CONFIRMED
        */
        ```
        
- no other external libraries allowed except for logging and visualization!