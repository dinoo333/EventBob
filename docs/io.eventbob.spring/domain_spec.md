# EventBob Spring Implementation - Domain Specification

**Module:** io.eventbob.spring (Bridge Implementation)
**Implements:** HTTP REST adapter for EventBob + Remote handler adapter
**Type:** Infrastructure layer (Bridge Pattern)
**Serves:** Event processing via HTTP (inbound and outbound)
**Last Updated:** 2026-02-18

## Implementation Overview

This module is the **Spring Boot implementation of EventBob infrastructure**. It provides:
1. HTTP REST interface for inbound event processing (EventController)
2. HTTP adapter for outbound remote handler invocation (HttpEventHandlerAdapter)

**Key Point:** This is NOT a bounded context itself. The bounded contexts are defined in `io.eventbob.core`. This module is one IMPLEMENTATION of HTTP adapters for the core event processing system.

## What This Implementation Provides

This Spring-based implementation is responsible for:
- **Exposing HTTP REST endpoint** for event processing
- **JSON serialization** for Event messages (EventDto)
- **Spring configuration** that loads handlers from JARs and wires EventBob
- **Built-in healthcheck handler** for system monitoring
- **Remote handler adapters** for inter-microlith communication via HTTP

**Not responsible for:** Event routing logic (that's in core domain), handler implementation (handlers are in separate JARs), handler discovery/registration system (not implemented).

## Domain Concepts

### Event (from core)

An **Event** is a message containing source, target, parameters, metadata, and payload.

**Structure (from io.eventbob.core):**
```java
public class Event {
    private final String source;
    private final String target;
    private final Map<String, Object> parameters;
    private final Map<String, Object> metadata;
    private final Object payload;
}
```

**JSON representation (via EventDto):**
```json
{
  "source": "client",
  "target": "echo",
  "parameters": {},
  "metadata": {},
  "payload": "hello world"
}
```

### EventHandler (from core)

An **EventHandler** processes events for a specific capability.

**Interface (from io.eventbob.core):**
```java
public interface EventHandler {
    Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException;
}
```

**Capability annotation:**
```java
@Capability("echo")
public class EchoHandler implements EventHandler { ... }
```

### EventDto (Anti-Corruption Layer)

An **EventDto** is a Data Transfer Object for JSON serialization of Event messages across HTTP boundaries.

**Purpose:** Keeps Jackson annotations and REST concerns out of domain Event class. Translates between domain Event (core) and JSON (HTTP).

**Translation methods:**
- `EventDto.fromEvent(Event)` - converts domain Event to DTO for HTTP response serialization
- `EventDto.toEvent()` - converts DTO from HTTP request to domain Event

**Placement:** Lives in infrastructure layer (io.eventbob.spring), never imported by core.

**Usage:** EventController and HttpEventHandlerAdapter use EventDto at HTTP boundary. Core domain never sees EventDto.

### RemoteCapability

A **RemoteCapability** maps a capability name to a remote endpoint URI.

**Structure:** `RemoteCapability(name: String, uri: URI)`

**Purpose:** Configuration object that declares which capabilities are hosted remotely. Used by RemoteHandlerLoader to create HTTP adapters.

**Example:** `RemoteCapability("upper", URI.create("http://text-processing-service:8080"))`

**Validation:** Name must not be blank, URI must not be null.

### HttpEventHandlerAdapter (Anti-Corruption Layer)

An **HttpEventHandlerAdapter** wraps HTTP calls to remote EventHandler endpoints. Implements EventHandler interface, providing location transparency.

**Purpose:** Translates between domain Event (core) and HTTP requests/responses (infrastructure). Remote handlers appear identical to local handlers from EventBob's routing perspective.

**Protocol:**
- POST {remoteEndpoint}/events
- Content-Type: application/json
- Request body: EventDto JSON
- Response body: EventDto JSON
- HTTP status codes: 2xx = success, 4xx/5xx = error (throws EventHandlingException)

**Translation:** Uses EventDto.fromEvent() for request serialization and EventDto.toEvent() for response deserialization.

**Error handling:** Network errors, HTTP 4xx/5xx responses, and JSON parsing failures all throw EventHandlingException (core's error type).

### RemoteHandlerLoader (Infrastructure)

A **RemoteHandlerLoader** creates EventHandler wrappers for remote capability endpoints.

**Purpose:** Implements HandlerLoader interface (core) by creating HttpEventHandlerAdapter instances for each RemoteCapability.

**Contract:** `Map<String, EventHandler> loadHandlers()` - uses constructor-injected List<RemoteCapability> and HttpClient to create adapters.

**Return value:** Map keyed by capability name, with HttpEventHandlerAdapter instances as values.

**Extensibility:** Future implementations could inspect URI scheme and create different adapter types (gRPC, JMS, Kafka).

## Ubiquitous Language

| Term | Meaning | Example |
|------|---------|---------|
| **Event** | Message containing source, target, payload | `{ "target": "echo", "payload": "hello" }` |
| **EventHandler** | Processes events for a specific capability | `EchoHandler`, `LowerHandler` |
| **Capability** | Name identifying what an EventHandler does | "echo", "lower", "healthcheck" |
| **EventDto** | JSON representation of Event for HTTP (anti-corruption layer) | Serialized Event for REST API |
| **RemoteCapability** | Mapping from capability name to remote endpoint URI | `("upper", "http://text-service:8080")` |
| **HttpEventHandlerAdapter** | EventHandler that delegates to remote endpoint via HTTP | Adapter wrapping remote calls |
| **RemoteHandlerLoader** | HandlerLoader that creates HTTP adapters from RemoteCapability configs | Loader for remote handlers |
| **Microlith** | Runtime that bundles multiple handlers | Single process with echo + lower handlers |
| **Location transparency** | Remote and local handlers indistinguishable at routing level | Both implement EventHandler |

## Anti-Corruption Layers

### Inbound HTTP Translation (EventController)

**Boundary:** HTTP requests → Domain Event

**Translation:** EventController receives EventDto JSON, converts to domain Event, invokes EventBob routing, converts result Event to EventDto JSON response.

**Protection:** Core domain never sees HttpServletRequest, Jackson annotations, or Spring types.

### Outbound HTTP Translation (HttpEventHandlerAdapter)

**Boundary:** Domain Event → HTTP requests

**Translation:** HttpEventHandlerAdapter receives domain Event, converts to EventDto JSON, makes HTTP POST, parses EventDto JSON response, converts to domain Event.

**Protection:** Core domain never sees HttpClient, HttpRequest, or HTTP status codes. Failures are translated to EventHandlingException (core's error type).

**Key insight:** Remote handlers are wrapped as EventHandler implementations. EventBob routing logic treats them identically to local handlers. HTTP is infrastructure detail, not domain concept.

## Context Boundary Contract

**Imports from Event Processing Context:**
- `io.eventbob.core.Event` (domain model)
- `io.eventbob.core.EventHandler` (handler interface)
- `io.eventbob.core.EventBob` (event processor)
- `io.eventbob.core.Capability` (annotation)
- `io.eventbob.core.Dispatcher` (handler coordination)
- `io.eventbob.core.HandlerLoader` (loader interface)

**Exports to HTTP clients:**
- REST API: `POST /events` accepting/returning EventDto JSON
- Healthcheck: `POST /events` with `target=healthcheck` returns `payload=true`

**Exports to other EventBob microliths:**
- HTTP handler endpoint: `POST /events` accepting/returning EventDto JSON
- Protocol: Same as inbound API (symmetric)

**Boundary crossing rules:**
- Spring module can import from core (outer depends on inner)
- Core cannot import from Spring module (inner never depends on outer)
- EventDto translates at HTTP boundary (Event never exposed directly via HTTP)
- HttpEventHandlerAdapter translates at remote handler boundary (HTTP never leaks into core routing logic)

## Domain Invariants (This Module)

1. **EventDto must round-trip cleanly** - `event.equals(EventDto.fromEvent(event).toEvent())` must be true for all Events
2. **HTTP adapters implement EventHandler** - Remote handlers must look identical to local handlers from EventBob's perspective
3. **RemoteCapability names must be unique** - Two RemoteCapability instances cannot have the same name
4. **HTTP errors map to EventHandlingException** - All transport errors (network, HTTP status, JSON parsing) throw core's error type

## Summary

The Spring implementation is a **REST adapter** that enables both inbound and outbound HTTP-based event processing:

**Inbound (EventController):**
1. Exposes REST endpoint for event submission
2. Translates HTTP requests to domain Events
3. Routes events through EventBob to appropriate handlers
4. Translates domain Events to HTTP responses

**Outbound (HttpEventHandlerAdapter):**
1. Wraps remote capability endpoints as EventHandler implementations
2. Translates domain Events to HTTP requests
3. Invokes remote EventBob instances
4. Translates HTTP responses to domain Events

**Key insight:** This module owns the "HTTP interface" (REST endpoints, JSON serialization, HTTP client), not the "event processing logic" (that's in core). It translates between HTTP and domain Events at the boundary, preserving location transparency.
