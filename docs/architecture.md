# EventBob Architecture

## Bridge Pattern: Core + Implementations

EventBob is built using the **Bridge Pattern** to separate abstraction from implementation:

- **Abstraction:** `io.eventbob.core` - Defines domain model, ports, and interfaces
- **Implementations:** `io.eventbob.spring`, `io.eventbob.dropwizard` (planned), etc. - Framework-specific implementations of EventBob infrastructure

**Key Insight:** The core defines WHAT (domain concepts, routing logic, port contracts). Implementations define HOW (using Spring Boot, Dropwizard, or other frameworks).

### Multiple Framework Implementations

```
┌─────────────────────────────────┐
│   io.eventbob.core              │  Abstraction (domain model + ports)
│   - Event, EventHandler         │
│   - CapabilityResolver port     │
│   - Capability enum (READ/WRITE/ADMIN)
└─────────────────────────────────┘
              ↑ implements
      ┌───────┴────────┐
      │                │
┌─────────────┐  ┌─────────────┐
│ io.eventbob │  │ io.eventbob │  Implementations (framework-specific)
│   .spring   │  │ .dropwizard │
│             │  │             │
│ Spring Boot │  │ Dropwizard  │
│ Spring JDBC │  │ JDBI        │
│ PostgreSQL  │  │ PostgreSQL  │
└─────────────┘  └─────────────┘
```

**Why this matters:**
1. **Framework independence** - Core domain is not coupled to Spring, Dropwizard, or any framework
2. **Multiple implementations can coexist** - Use Spring for one deployment, Dropwizard for another
3. **Clear dependency direction** - Implementations depend on core, never reverse

### What Belongs Where: Core vs Implementations

| Concern | Belongs in Core | Belongs in Implementation |
|---------|----------------|---------------------------|
| Event domain model | ✅ Yes | ❌ No |
| EventHandler interface | ✅ Yes | ❌ No |
| CapabilityResolver port | ✅ Yes (interface only) | ✅ Yes (concrete impl) |
| Capability enum (READ/WRITE/ADMIN) | ✅ Yes | ❌ No |
| JAR scanning for capabilities | ❌ No | ✅ Yes |
| Capability persistence (PostgreSQL) | ❌ No | ✅ Yes |
| Instance tracking and health | ❌ No | ✅ Yes |
| Deployment state management | ❌ No | ✅ Yes |
| Bootstrap and configuration | ❌ No | ✅ Yes |
| Framework types (Spring, Dropwizard) | ❌ No | ✅ Yes |

**Rule of thumb:** If it's about "what routing means" → core. If it's about "how to do routing with framework X" → implementation.

### Dependency Rules (Enforced)

**✅ Allowed:**
- Implementations → Core (Spring/Dropwizard depend on core interfaces)
- Adapters → Core (HTTP/gRPC adapters implement EventHandler)
- Services → Core (business services implement EventHandler)

**❌ Forbidden:**
- Core → Implementations (core cannot import Spring, Dropwizard, JDBI, etc.)
- Core → Adapters (core cannot know about HTTP, gRPC)
- Implementation A → Implementation B (Spring cannot import Dropwizard)

**Enforcement:** ArchUnit tests in each module verify these rules at build time.

### Choosing an Implementation

**Use io.eventbob.spring if:**
- You're already using Spring Boot in your services
- You want Spring's dependency injection and transaction management
- You prefer Spring JDBC for database access

**Use io.eventbob.dropwizard if:**
- You prefer lightweight frameworks over Spring
- You want JDBI for database access
- You're already using Dropwizard in your services

**Both implementations provide identical capabilities:**
- JAR scanning for `@EventHandlerCapability` annotations
- Capability registration and conflict detection
- Instance tracking and health management
- Blue/green deployment support
- Implementation of `CapabilityResolver` port

## Current Implementation Status

**As of 2026-02-11:**

### ✅ Implemented
- **Core module** (`io.eventbob.core`): Event, EventHandler, EventHandlingRouter, DecoratedEventHandler, MetadataKeys, exception hierarchy
- **Dependency Rule**: Core has zero external dependencies, clean boundaries
- **Test coverage**: 23 tests covering all core components

### ⏳ Planned (Not Yet Implemented)
- Adapter modules (HTTP, gRPC, queue)
- Macro-service composition and configuration
- Classloader isolation for services
- Route registry (local vs remote routing)
- Async event patterns (fire-and-forget, queues)

---

## System Overview

EventBob is a distributed event routing fabric built on Clean Architecture principles, enabling microservices to communicate via events whether they are deployed together (in-process) or separately (network).

## Layer Structure

```
┌─────────────────────────────────────┐
│   Adapters (Outer Layer)            │  HTTP, gRPC, Queue adapters
│   - io.eventbob.adapter.http        │  Convert transport ↔ Event
│   - io.eventbob.adapter.grpc        │  Implement EventHandler
│   - io.eventbob.adapter.queue       │
└─────────────────────────────────────┘
              ↓ depends on
┌─────────────────────────────────────┐
│   Core Domain (Inner Layer)         │
│   io.eventbob.core                  │
│   - Event                            │  Domain model
│   - EventHandler                     │  Core abstraction
│   - EventHandlingRouter              │  Routing logic
│   - MetadataKeys                     │  Routing vocabulary
│   - Exception hierarchy              │
└─────────────────────────────────────┘
```

### Dependency Rule

**All dependencies point INWARD.**

- Adapters depend on core
- Core depends on nothing (except JDK)
- Services implement EventHandler (core interface)
- No core code imports from adapter packages

## Module Boundaries

### Core Module (`io.eventbob.core`)

**Purpose:** Define the event routing domain model and abstractions.

**Exports:**
- `Event` - Immutable event data structure
- `EventHandler` - The universal contract for event processing
- `EventHandlingRouter` - Routes events by target to handlers
- `MetadataKeys` - Standard routing and observability metadata keys
- Exception hierarchy

**Dependencies:** None (zero external runtime dependencies)

**Stability:** HIGH - This is the innermost layer. Changes ripple outward.

### Adapter Modules (Future)

**Purpose:** Translate between transport protocols and Event domain model.

**Example: `io.eventbob.adapter.http`**
- Inbound: HTTP requests → Events
- Outbound: Events → HTTP requests to remote macros
- Implements EventHandler for outbound calls
- Defines transport-specific metadata (http.status, http.content-type)

**Dependencies:**
- `io.eventbob.core` (required)
- HTTP client libraries (e.g., java.net.http, OkHttp)

**Stability:** MEDIUM - Adapters change when transport requirements change

## Key Architectural Patterns

### 1. Hexagonal Architecture (Ports & Adapters)

**Port:** `EventHandler` interface
- Input port: Services receive events via EventHandler.handle()
- Output port: Adapters forward events via EventHandler.handle()

**Adapters:** HTTP, gRPC, Queue implementations
- Convert transport protocols to/from Event domain model
- Sit at system boundaries
- Isolated from core

### 2. Dependency Inversion

The core defines interfaces (`EventHandler`). Adapters and services implement them. The core does not depend on implementations.

### 3. Open/Closed Principle

**Closed for modification:** Core routing logic does not change when new transports are added.

**Open for extension:**
- New adapters implement EventHandler
- New metadata keys use namespaced conventions (http.*, grpc.*, queue.*)
- Core vocabulary (MetadataKeys) remains stable

### 4. Composition Over Inheritance

- `EventHandlingRouter` implements `EventHandler`, enabling router nesting
- `DecoratedEventHandler` wraps any EventHandler for cross-cutting concerns
- No inheritance hierarchies - composition via interfaces

## Boundaries & Contracts

### Event Structure Contract

```java
Event {
  source: String           // Producer service identifier
  target: String           // Consumer service identifier
  metadata: Map            // Routing + observability metadata
  parameters: Map          // Business data + path parameters
  payload: Serializable    // Request body
}
```

**Boundary Rules:**
1. **Metadata** = infrastructure concerns (correlation-id, trace-id, method, path)
2. **Parameters** = business data (user-id, order-id, quantity)
3. **Payload** = structured request body (POJOs, JSON, Protobuf after serialization)

### Metadata Namespacing Convention (Design)

**Core domain defines:**
- correlation-id, reply-to (routing)
- method, path (operation semantics)
- trace-id, span-id (observability)

**When adapters are implemented, they WILL namespace their metadata:**
- HTTP: `http.status`, `http.content-type`, `http.headers`
- gRPC: `grpc.service`, `grpc.method`, `grpc.status`
- Queue: `queue.topic`, `queue.partition-key`

**Design intent:** Core will NOT validate or enumerate transport-specific keys. Adapters will own their namespaces.

## Future Architecture: Deployment & Composition

**The following sections describe planned architecture, not current implementation.**

### Planned: Macro-Service Composition

A macro-service will be a process containing:
1. **Multiple services** (JARs loaded via isolated classloaders)
2. **EventBob router** (routes events to local handlers or remote adapters)
3. **Adapters** (REST API endpoint, gRPC server, queue consumers)

### Planned: Routing Decision

```
Event arrives with target="user-service"
  ↓
Router will consult route registry:
  - Is "user-service" local to this macro?
    YES → route to local handler (in-process)
    NO → route to remote macro via adapter (network)
```

### Planned: Communication Patterns

**In-Process (Local):**
```
[REST Adapter] → [Router] → [UserService Handler]
                              (same JVM, direct method call)
```

**Cross-Process (Remote):**
```
[Macro-A Router] → [HTTP Adapter] → Network → [Macro-B HTTP Adapter] → [Macro-B Router] → [PaymentService Handler]
```

### Planned: Classloader Isolation

**Goal:** Independent versioning of services within same JVM.

**Planned Architecture:**
```
System ClassLoader
    ↓
EventBob Core ClassLoader
  (Event, EventHandler, Router)
    ↓
    ├─ UserService ClassLoader (user-service.jar + dependencies)
    ├─ InventoryService ClassLoader (inventory-service.jar + dependencies)
    └─ PaymentService ClassLoader (payment-service.jar + dependencies)
```

**Design Rules:**
1. Core classes will be loaded by parent, visible to all service classloaders
2. Services will be isolated from each other (cannot reference each other's classes)
3. Services will communicate via Event (core type, shared across classloaders)

## Extension Points

### Adding New Transports

1. Create `io.eventbob.adapter.<transport>` module
2. Implement EventHandler for outbound calls
3. Define transport-specific metadata keys in adapter package
4. Document metadata namespace convention (e.g., `kafka.*`, `amqp.*`)

**Core remains unchanged.**

### Adding New Services (Future)

When service loading is implemented:
1. Implement EventHandler in service code
2. Package as JAR with isolated dependencies
3. Register with macro-service configuration
4. Will be loaded via classloader, routed by EventBob

**No core or adapter changes will be needed.**

### Cross-Cutting Concerns

Use `DecoratedEventHandler` to wrap handlers with:
- Logging
- Metrics
- Retry logic
- Circuit breaking
- Authentication/authorization

**Pattern:**
```java
EventHandler handler = DecoratedEventHandler.builder()
  .delegate(actualServiceHandler)
  .before(event -> log.info("Handling: {}", event))
  .afterSuccess((req, res) -> metrics.recordSuccess())
  .onError((event, error) -> metrics.recordFailure())
  .build();
```

## Future Architecture Evolution

### When First Adapter is Built

**Document:**
- Adapter contract patterns (request translation, response mapping)
- Metadata namespacing conventions
- Error propagation strategies
- Serialization approach (JSON, Protobuf, etc.)

**Create:**
- `docs/io.eventbob.adapter.http/architecture.md`
- Reference implementation demonstrating best practices

### When Classloader Isolation is Implemented

**Document:**
- Classloader hierarchy design
- Service loading mechanism
- Prevention of classloader leaks
- Shared vs isolated dependencies

### When Route Registry is Implemented

**Document:**
- Registry structure (static config, service discovery, hybrid)
- Local vs remote routing decision logic
- Configuration format and loading
- Runtime updates (if supported)

## Non-Goals

**EventBob does NOT:**
- Provide message persistence (use external queues)
- Guarantee exactly-once delivery (adapter concern)
- Manage transactions across services (use saga pattern in services)
- Provide service discovery (use external registry + config)
- Define business domain models (services own their domains)

**EventBob IS:**
- A routing fabric
- A protocol translation layer (via adapters)
- A deployment flexibility mechanism (in-process vs remote)
