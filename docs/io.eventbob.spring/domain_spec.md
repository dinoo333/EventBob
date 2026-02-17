# EventBob Spring Implementation - Domain Specification

**Module:** io.eventbob.spring (Bridge Implementation)
**Implements:** HTTP REST adapter for EventBob
**Type:** Infrastructure layer (Bridge Pattern)
**Serves:** Event processing via HTTP
**Last Updated:** 2026-02-16

## Implementation Overview

This module is the **Spring Boot implementation of EventBob infrastructure**. It provides an HTTP REST interface for event processing.

**Key Point:** This is NOT a bounded context itself. The bounded contexts are defined in `io.eventbob.core`. This module is one IMPLEMENTATION of an HTTP adapter for the core event processing system.

## What This Implementation Provides

This Spring-based implementation is responsible for:
- **Exposing HTTP REST endpoint** for event processing
- **JSON serialization** for Event messages
- **Spring configuration** that loads handlers from JARs and wires EventBob
- **Built-in healthcheck handler** for system monitoring

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

### EventDto (REST layer)

An **EventDto** is a Data Transfer Object for JSON serialization of Event messages.

**Purpose:** Keeps Jackson annotations and REST concerns out of domain Event class.

**Mapping:** EventController translates EventDto ↔ Event at the REST boundary.

## Ubiquitous Language

| Term | Meaning | Example |
|------|---------|---------|
| **Event** | Message containing source, target, payload | `{ "target": "echo", "payload": "hello" }` |
| **EventHandler** | Processes events for a specific capability | `EchoHandler`, `LowerHandler` |
| **Capability** | Name identifying what an EventHandler does | "echo", "lower", "healthcheck" |
| **EventDto** | JSON representation of Event for HTTP | Serialized Event for REST API |
| **Microlith** | Runtime that bundles multiple handlers | Single process with echo + lower handlers |

## Context Boundary Contract

**Imports from Event Processing Context:**
- `io.eventbob.core.Event` (domain model)
- `io.eventbob.core.EventHandler` (handler interface)
- `io.eventbob.core.EventBob` (event processor)
- `io.eventbob.core.Capability` (annotation)
- `io.eventbob.core.Dispatcher` (handler coordination)

**Exports to HTTP clients:**
- REST API: `POST /events` accepting/returning EventDto JSON
- Healthcheck: `POST /events` with `target=healthcheck` returns `payload=true`

**Boundary crossing rules:**
- Spring module can import from core (outer depends on inner)
- Core cannot import from Spring module (inner never depends on outer)
- EventDto translates at REST boundary (Event never exposed directly via HTTP)

## Summary

The Spring implementation is a **REST adapter** that enables HTTP-based event processing:
1. Exposes REST endpoint for event submission
2. Loads handlers from JAR files
3. Routes events through EventBob to appropriate handlers
4. Returns handler results as JSON

**Key insight:** This module owns the "HTTP interface" (REST endpoint, JSON serialization), not the "event processing logic" (that's in core). It translates HTTP requests into domain Events, invokes core processing, and translates domain Events back to HTTP responses.
