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
│   EventHandler          │  Handlers loaded from JARs
│   (via HandlerLoader)   │  + HealthcheckHandler
└─────────────────────────┘
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

## Testing

Run tests:

```bash
mvn test
```

## Why "Spring" Not "REST" or "HTTP"?

This module is named `io.eventbob.spring` because:
1. It's the **Spring Boot implementation** of EventBob (Bridge Pattern)
2. It uses Spring's framework capabilities (DI, REST, JSON serialization)
3. It enables peer implementations like `io.eventbob.dropwizard` to coexist
4. The domain abstraction lives in `io.eventbob.core`

## Components

### EventBobConfig
Spring configuration that:
- Loads handlers from JAR files via `HandlerLoader`
- Registers handlers with `EventBob` by capability name
- Provides hard-coded `HealthcheckHandler` for system monitoring

### EventController
Spring REST controller that:
- Exposes `POST /events` endpoint
- Accepts `EventDto` JSON in request body
- Routes events through `EventBob` to appropriate handlers
- Returns result as `EventDto` JSON
- Non-blocking via `CompletableFuture`

### EventDto
Data Transfer Object that:
- Maps between JSON and domain `Event` objects
- Keeps Jackson annotations out of domain model
- Provides plain JavaBean getters/setters for Spring MVC

### HealthcheckHandler
Built-in handler that:
- Responds to "healthcheck" capability
- Returns `payload=true` to indicate service is alive
- Used by monitoring systems and load balancers

## Dependencies

- **Spring Boot**: REST endpoints, dependency injection, JSON serialization
- **EventBob Core**: Domain model and event processing logic

## Alternative Implementations

The Bridge Pattern enables multiple framework implementations:
- `io.eventbob.spring` (this module) - Spring Boot + Spring MVC
- `io.eventbob.dropwizard` (planned) - Dropwizard + Jersey
- `io.eventbob.micronaut` (future) - Micronaut

All implementations provide HTTP REST endpoints for event processing using different frameworks.
