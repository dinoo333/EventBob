# EventBob Spring Implementation

**Spring Boot implementation of EventBob** (Bridge Pattern)

## What Is This Module?

This is the **Spring-based implementation of EventBob** - a Spring Boot adapter that provides HTTP REST endpoints for event processing.

**Architecture Pattern:** Bridge
- **Abstraction:** `io.eventbob.core` (domain model, EventBob, EventHandler)
- **Implementation:** `io.eventbob.spring` (this module - Spring-based REST adapter)

## What This Implementation Provides

- **Spring configuration** (`EventBobConfig`) that loads handlers from JAR files
- **REST endpoint** (`EventController`) for HTTP event processing
- **JSON serialization** (`EventDto`) for Event messages
- **Built-in healthcheck handler** for system health monitoring

## Architecture

```
┌─────────────────────────┐
│   EventController       │  POST /events endpoint
│   (Spring REST)         │
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│   EventBob              │  Core event processor
│   (io.eventbob.core)    │
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│   EventHandler          │  Handlers loaded from:
│   (via HandlerLoader +  │  - JARs (HandlerLoader)
│    RemoteHandlerLoader) │  - Remote HTTP (RemoteHandlerLoader)
│                         │  - Built-in (HealthcheckHandler)
└─────────────────────────┘
            │
            ├──> Local Handlers (from JARs)
            │
            └──> HttpEventHandlerAdapter ──> Remote Microlith
                     (HTTP POST)              (via /events endpoint)
```

## Usage

### 1. Configure Handler JAR Paths

Provide a bean specifying which handler JARs to load:

```java
@Configuration
public class MyAppConfig {
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Path.of("/app/handlers/echo-handler.jar"),
      Path.of("/app/handlers/lower-handler.jar")
    );
  }
}
```

### 2. Implement Event Handlers

Create handlers that implement `EventHandler` and annotate with `@Capability`:

```java
@Capability("echo")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) {
    return event.toBuilder()
        .payload(event.getPayload())
        .build();
  }
}
```

### 3. Send Events via HTTP

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "client",
    "target": "echo",
    "payload": "hello world"
  }'
```

Response:
```json
{
  "source": "client",
  "target": "echo",
  "payload": "hello world"
}
```

## Remote Handler Configuration

Microliths can communicate with each other by configuring remote handlers. A remote handler routes events to another microlith via HTTP.

### Configure Remote Capabilities

Provide a bean specifying remote handlers and their endpoints:

```java
@Configuration
public class RemoteCapabilitiesConfig {
  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
      new RemoteCapability("upper", URI.create("http://localhost:8081"))
    );
  }
}
```

### Inter-Microlith Example

**Scenario:** Echo microlith (port 8080) calls Upper microlith (port 8081) via HTTP.

**Echo Microlith Configuration (port 8080):**
```java
@Configuration
public class EchoMicrolithConfig {
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Path.of("/app/handlers/echo-handler.jar")
    );
  }

  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
      new RemoteCapability("upper", URI.create("http://localhost:8081"))
    );
  }
}
```

**Upper Microlith Configuration (port 8081):**
```java
@Configuration
public class UpperMicrolithConfig {
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Path.of("/app/handlers/upper-handler.jar")
    );
  }

  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(); // No remote handlers
  }
}
```

**Echo Handler Implementation (uses dispatcher to call remote upper):**
```java
@Capability("echo")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) {
    // Call remote upper capability via HTTP
    Event upperResult = dispatcher.dispatch(
      event.toBuilder()
        .target("upper")
        .build()
    ).join();

    return event.toBuilder()
        .payload("Echo: " + upperResult.getPayload())
        .build();
  }
}
```

**Client Request to Echo Microlith:**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "client",
    "target": "echo",
    "payload": "hello world"
  }'
```

**What Happens:**
1. Request arrives at Echo microlith (8080)
2. Echo handler dispatches event to "upper" capability
3. `HttpEventHandlerAdapter` makes HTTP POST to http://localhost:8081/events
4. Upper microlith (8081) processes event and returns uppercased payload
5. Echo handler receives result and prepends "Echo: "
6. Response: `{"source":"client","target":"echo","payload":"Echo: HELLO WORLD"}`

## Testing

Run tests:

```bash
mvn test
```

## Why "Spring" Not "REST" or "HTTP"?

This module is named `io.eventbob.spring` because:
1. It's the **Spring Boot implementation** of EventBob (Bridge Pattern)
2. It uses Spring's framework capabilities (DI, REST, JSON serialization)
3. The domain abstraction lives in `io.eventbob.core`

## Components

### EventBobConfig
Spring configuration that:
- Loads handlers from JAR files via `HandlerLoader`
- Loads remote handlers via `RemoteHandlerLoader`
- Registers all handlers with `EventBob` by capability name
- Provides hard-coded `HealthcheckHandler` for system monitoring

### EventController
Spring REST controller that:
- Exposes `POST /events` endpoint
- Accepts `EventDto` JSON in request body
- Routes events through `EventBob` to appropriate handlers
- Returns result as `EventDto` JSON
- Non-blocking via `CompletableFuture`

### EventDto
Data Transfer Object (Record) that:
- Maps between JSON and domain `Event` objects
- Keeps Jackson annotations out of domain model
- Located in `io.eventbob.spring.adapter` package
- Immutable record type for type safety

### HttpEventHandlerAdapter
Remote handler adapter that:
- Implements `EventHandler` interface
- Routes events to remote microliths via HTTP POST
- Posts to `{remoteEndpoint}/events` with `EventDto` JSON payload
- Converts response back to domain `Event`
- Enables inter-microlith communication without tight coupling

### RemoteHandlerLoader
Component that:
- Loads remote handlers from `List<RemoteCapability>` bean
- Creates `HttpEventHandlerAdapter` instances for each remote capability
- Registers remote handlers with `EventBob` by capability name
- Enables declarative remote handler configuration

### HealthcheckHandler
Built-in handler that:
- Responds to "healthcheck" capability
- Returns `payload=true` to indicate service is alive
- Used by monitoring systems and load balancers

## Dependencies

- **Spring Boot**: REST endpoints, dependency injection, JSON serialization
- **EventBob Core**: Domain model and event processing logic
