# EventBob Spring Implementation Architecture

**Module:** io.eventbob.spring
**Type:** Infrastructure Implementation (Bridge Pattern)
**Last Updated:** 2026-02-16

## Bridge Pattern: Core + Spring

This module is the **Spring Boot implementation of EventBob**. It provides a REST HTTP adapter for event processing.

**Architecture Pattern:** Bridge Pattern
- **Abstraction:** `io.eventbob.core` (domain model, EventBob, EventHandler)
- **Implementation:** `io.eventbob.spring` (Spring Boot REST adapter)

This is NOT a standalone system. This is **EventBob with a Spring Boot HTTP interface**.

## Module Purpose

This Spring implementation provides the HTTP REST adapter for EventBob:
- **HTTP endpoint** for event processing (POST /events)
- **JSON serialization** for Event messages
- **Spring configuration** to wire EventBob with handlers loaded from JARs
- **Built-in healthcheck handler** for monitoring

**Responsibilities:**
- Expose REST endpoint for event processing
- Serialize/deserialize Event messages to/from JSON
- Load handlers from JAR files via HandlerLoader
- Register handlers with EventBob by capability name

**Not responsible for:**
- Event routing logic (core domain in io.eventbob.core)
- Handler implementation (handlers are in separate JARs)
- Handler discovery/registration system (not implemented)

## Layer Assignment

```
┌─────────────────────────────────┐
│   REST Adapter Layer            │
│   io.eventbob.spring            │  EventController (REST endpoint)
│                                 │  EventDto (JSON serialization)
│                                 │  EventBobConfig (Spring configuration)
└─────────────────────────────────┘
              ↓
┌─────────────────────────────────┐
│   Domain Layer                  │
│   io.eventbob.core              │  EventBob (event processor)
│                                 │  EventHandler (handler interface)
│                                 │  Event (domain model)
└─────────────────────────────────┘
```

**Dependency direction:** Infrastructure → Core (outer depends on inner). Core never imports from Spring module.

**Anti-Corruption Layer:** Spring types (RestController, RequestBody, CompletableFuture) stay in Spring module. Domain Event objects are never exposed directly via REST - EventDto provides translation.

## Component Design

### EventBobConfig

**Responsibility:** Spring configuration for EventBob and its handlers.

**Pattern:** Spring @Configuration

**Key operations:**
- `eventBob(HealthcheckHandler) → EventBob` - Create configured EventBob bean
- `healthcheckHandler() → HealthcheckHandler` - Create healthcheck handler bean

**Dependencies:**
- `HandlerLoader` from core (loads handlers from JARs)
- `EventBob` from core (event processor)

**Data flow:**
```
List<Path> handlerJarPaths → HandlerLoader.loadHandlers()
                           → Map<String, EventHandler>
                           → EventBob.Builder.handler()
                           → EventBob instance
```

**Why this design:**
- Spring DI manages component wiring
- HandlerLoader handles JAR scanning and instantiation
- Configuration is explicit (no hidden magic)

---

### EventController

**Responsibility:** REST endpoint for event processing.

**Pattern:** Spring @RestController

**Key operations:**
- `processEvent(EventDto) → CompletableFuture<EventDto>` - Process event via HTTP POST

**Dependencies:**
- `EventBob` (injected via constructor)

**Data flow:**
```
HTTP POST /events with EventDto JSON
         → toEvent(EventDto) → Event
         → eventBob.processEvent(Event)
         → CompletableFuture<Event>
         → fromEvent(Event) → EventDto
         → HTTP response with EventDto JSON
```

**Why this design:**
- EventDto keeps Jackson annotations out of domain Event
- CompletableFuture makes endpoint non-blocking
- Simple mapping methods keep translation explicit

---

### EventDto

**Responsibility:** Data Transfer Object for Event JSON serialization.

**Pattern:** DTO (plain JavaBean)

**Structure:**
```java
public class EventDto {
    private String source;
    private String target;
    private Map<String, Object> parameters;
    private Map<String, Object> metadata;
    private Object payload;
}
```

**Lifecycle:** Created by Jackson from HTTP request → mapped to Event → processed → mapped from Event → serialized to HTTP response

**Why plain JavaBean:** Spring MVC and Jackson expect JavaBean pattern (getters/setters, no-arg constructor).

---

### HealthcheckHandler

**Responsibility:** Built-in handler for system health monitoring.

**Pattern:** EventHandler implementation

**Behavior:** Returns event with `payload=true` to indicate service is alive.

**Why hard-coded:** System health monitoring is infrastructure concern, not pluggable business logic.

---

## Infrastructure Choices

### Why Spring Boot?

- Mature REST framework (Spring MVC)
- JSON serialization (Jackson)
- Dependency injection (Spring DI)
- Non-blocking async support (CompletableFuture)
- Minimal configuration

### Why EventDto (not expose Event directly)?

- Keeps Jackson annotations out of domain model
- Provides clean separation between REST concerns and domain
- Allows REST representation to differ from domain if needed

### Why CompletableFuture?

- Non-blocking HTTP endpoint (Spring MVC suspends async)
- Matches EventBob's async API
- No thread blocking while handlers process events

---

## Summary

This Spring implementation provides a **simple HTTP REST adapter** for EventBob:

1. **Expose REST endpoint** for event processing (POST /events)
2. **Load handlers from JARs** via HandlerLoader
3. **Serialize events to/from JSON** via EventDto
4. **Provide system health monitoring** via built-in healthcheck handler

**Key architectural decisions:**
- Spring Boot for REST infrastructure
- Bridge Pattern keeps Spring types in infrastructure layer
- EventDto provides anti-corruption layer for REST/domain boundary
- Dependency direction: Spring module → core (never reverse)
- Non-blocking via CompletableFuture

**What this is NOT:**
- Not a handler registry system
- Not a deployment orchestration system
- Not a discovery system
- Just a REST adapter for event processing
