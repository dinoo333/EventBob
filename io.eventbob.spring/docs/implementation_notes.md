# EventBob Spring Implementation Notes

**Module:** io.eventbob.spring
**Last Updated:** 2026-02-16

## Module Structure

```
io.eventbob.spring/
  ├── EventBobConfig.java       - Spring configuration (loads handlers, creates EventBob bean)
  ├── EventController.java      - REST endpoint (POST /events)
  ├── EventDto.java             - DTO for Event JSON serialization
  └── handlers/
      └── HealthcheckHandler.java - Built-in healthcheck handler
```

## Implementation Patterns

### Spring Configuration Pattern

**EventBobConfig** uses Spring @Configuration to wire EventBob:

```java
@Configuration
public class EventBobConfig {
  private final List<Path> handlerJarPaths;

  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler) {
    // Load handlers from JARs
    // Register with EventBob
    // Return configured EventBob instance
  }
}
```

**Why this pattern:**
- Spring DI manages component lifecycle
- Configuration is explicit (no hidden magic)
- Handler JAR paths are provided by application via bean

### DTO Pattern

**EventDto** provides anti-corruption layer between REST and domain:

```java
// REST layer (EventDto)
public class EventDto {
  private String source;
  private String target;
  // ... getters/setters for Jackson
}

// Domain layer (Event from core)
public class Event {
  private final String source;
  private final String target;
  // ... immutable builder pattern
}
```

**Translation happens at REST boundary:**
- `EventController.toEvent(EventDto)` - REST → domain
- `EventController.fromEvent(Event)` - domain → REST

**Why this pattern:**
- Keeps Jackson annotations out of domain model
- Domain Event can evolve independently of REST representation
- REST concerns (JSON, HTTP) don't leak into core

### Async Pattern

**EventController** returns `CompletableFuture<EventDto>`:

```java
@PostMapping("/events")
public CompletableFuture<EventDto> processEvent(@RequestBody EventDto eventDto) {
  Event event = toEvent(eventDto);
  return eventBob.processEvent(event, errorHandler)
      .thenApply(this::fromEvent);
}
```

**Why this pattern:**
- Non-blocking HTTP endpoint (Spring MVC suspends async)
- Matches EventBob's async API (returns CompletableFuture<Event>)
- No thread blocking while handlers process events

## Component Responsibilities

### EventBobConfig
- Load handlers from JAR files via `HandlerLoader.jarLoader()`
- Register handlers with `EventBob.Builder`
- Create `EventBob` bean for Spring DI

### EventController
- Expose `POST /events` REST endpoint
- Translate EventDto ↔ Event at REST boundary
- Delegate event processing to EventBob
- Return result as JSON

### EventDto
- Plain JavaBean for Jackson JSON serialization
- No business logic (pure data)
- Maps 1:1 with Event structure

### HealthcheckHandler
- Built-in handler for system monitoring
- Returns `payload=true` to indicate service is alive
- Hard-coded (not loaded from JARs)

## Testing Strategy

Tests verify:
- Spring configuration loads successfully
- REST endpoint accepts and returns JSON
- EventDto ↔ Event translation is correct
- HealthcheckHandler responds correctly

## Bridge Pattern Implementation

### Anti-Corruption Layer

**Boundary:** Spring types stay in Spring module, core types stay in core.

**Example:**
```java
// Spring module (io.eventbob.spring)
@RestController
public class EventController {
  private final EventBob eventBob;  // Core type OK

  @PostMapping("/events")  // Spring annotation stays here
  public CompletableFuture<EventDto> processEvent(@RequestBody EventDto dto) {
    Event event = toEvent(dto);  // Translate at boundary
    return eventBob.processEvent(event, errorHandler)
        .thenApply(this::fromEvent);  // Translate at boundary
  }
}
```

**Violation example (NOT allowed):**
```java
// WRONG: Core importing from Spring
package io.eventbob.core;

import org.springframework.web.bind.annotation.RestController;  // VIOLATION

public class BadEventBob {
  // Spring types leaked to core
}
```

**Enforcement:** Module dependencies prevent core → Spring imports.

## Known Patterns

### Builder Pattern (Event from core)

Event uses builder pattern for construction:
```java
Event event = Event.builder()
    .source("client")
    .target("echo")
    .payload("hello")
    .build();
```

### JavaBean Pattern (EventDto)

EventDto uses JavaBean pattern for Jackson:
```java
public class EventDto {
  private String source;

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
```

## Summary

This implementation provides a **simple HTTP REST adapter** for EventBob:

- **EventBobConfig** loads handlers and wires EventBob
- **EventController** exposes REST endpoint and handles JSON translation
- **EventDto** provides anti-corruption layer for REST/domain boundary
- **HealthcheckHandler** provides system monitoring capability

**Key trade-offs:**
- Simplicity over features (no registration system, no discovery, no persistence)
- Explicit configuration over convention (handler JARs must be provided via bean)
- Anti-corruption via DTO (EventDto translates at boundary, Event stays pure domain)
