# Core Module Architecture

## Module: io.eventbob.core

**Purpose:** Define the event routing domain model with two cohesive subdomains: event routing and endpoint resolution.

**Version:** 1.0.0-SNAPSHOT

**Last Updated:** 2026-02-12

---

## Core as Abstraction (Bridge Pattern)

This module is the **abstraction** in the Bridge Pattern. It defines WHAT EventBob does, not HOW it's implemented.

### What Core Provides

**Domain Model:**
- `Event` - Immutable routing envelope with source, target, metadata, parameters, payload
- `EventHandler` - Universal interface for event processing
- `Capability` - Operation types (READ, WRITE, ADMIN)
- `MetadataKeys` - Standard routing vocabulary (correlation-id, method, path, trace-id)

**Ports (Interfaces for Implementation):**
- `CapabilityResolver` - Resolve service capabilities to physical endpoints
- `EventHandler` - Implemented by services, routers, decorators, adapters

**Routing Logic:**
- `EventHandlingRouter` - Routes events by target to handlers
- `DecoratedEventHandler` - Wraps handlers with cross-cutting concerns

### What Core Does NOT Provide

**Infrastructure (Implementation Concern):**
- ❌ Database access (Spring JDBC, JDBI) - implemented by framework modules
- ❌ JAR scanning - implemented by framework modules
- ❌ HTTP/gRPC/Queue adapters - separate adapter modules
- ❌ Configuration loading - implemented by framework modules
- ❌ Deployment lifecycle - implemented by framework modules

**Framework-Specific:**
- ❌ Spring annotations (@Service, @Autowired, @Transactional)
- ❌ Dropwizard resources (Resource, Environment, Application)
- ❌ Any framework types whatsoever

### Dependency Contract

**Core Dependencies:**
- ✅ JDK only (java.*, javax.*)
- ✅ SLF4J for logging abstraction
- ❌ No frameworks
- ❌ No external libraries

**Who Depends on Core:**
- Framework implementations (`io.eventbob.spring`, `io.eventbob.dropwizard`)
- Transport adapters (`io.eventbob.adapter.http`, `io.eventbob.adapter.grpc`)
- Business services (implement `EventHandler`)

**Who Core Depends On:**
- Nobody (zero outgoing dependencies)

### Multiple Implementations Pattern

```
┌─────────────────────────────────────────────┐
│        io.eventbob.core                     │
│        (Abstraction Layer)                  │
│                                             │
│  Defines:                                   │
│  - Event, EventHandler, Capability          │
│  - CapabilityResolver port                  │
│  - Routing logic, decoration                │
└─────────────────────────────────────────────┘
                      ↑
        ┌─────────────┴─────────────┐
        │                           │
┌───────────────────┐     ┌───────────────────┐
│  io.eventbob      │     │  io.eventbob      │
│    .spring        │     │  .dropwizard      │
│                   │     │                   │
│ Implementation 1  │     │ Implementation 2  │
│ - Spring Boot     │     │ - Dropwizard      │
│ - Spring JDBC     │     │ - JDBI            │
│ - ClassGraph      │     │ - ClassGraph      │
│ - PostgreSQL      │     │ - PostgreSQL      │
│                   │     │                   │
│ Implements:       │     │ Implements:       │
│ CapabilityResolver│     │ CapabilityResolver│
└───────────────────┘     └───────────────────┘
```

**Both implementations provide identical capabilities from core's perspective:**
- Implement `CapabilityResolver` port
- Return `Endpoint` objects (logical endpoint addresses)
- Use core's ubiquitous language (Capability, RoutingKey)

**Implementations differ in HOW:**
- Spring uses Spring JDBC, Dropwizard uses JDBI
- Spring uses @Service/@Autowired, Dropwizard uses manual wiring
- Internal deployment states (GRAY/RETIRED) never cross to core

### Port Contract Guidelines

When core defines a port (interface), it must:

1. **Use core types only** - No framework types in method signatures
2. **Be framework-agnostic** - Assume multiple implementations will exist
3. **Return core domain objects** - Not primitives or framework types
4. **Document contract clearly** - JavaDoc explains semantics, not implementation

**Example: CapabilityResolver Port**

```java
/**
 * Resolves service capabilities to physical endpoints.
 *
 * <p>Implementations query their backing store (database, service registry, etc.)
 * to find which physical endpoints provide the requested capability operation.
 *
 * <p>Supports progressive deployments by returning endpoints with deployment state
 * (GREEN = production, BLUE = canary).
 */
public interface CapabilityResolver {
    /**
     * Resolve the given routing key to a list of available endpoints.
     *
     * @param key Routing key identifying the service capability operation
     * @return List of endpoints providing this capability, empty if none found
     * @throws EndpointResolutionException if resolution fails
     */
    List<Endpoint> resolveEndpoints(RoutingKey key) throws EndpointResolutionException;
}
```

**What makes this a good port:**
- ✅ Only core types: `RoutingKey`, `Endpoint`, `EndpointResolutionException`
- ✅ No mention of Spring, JDBI, PostgreSQL
- ✅ JavaDoc describes WHAT, not HOW
- ✅ Multiple implementations can provide this differently

### Anti-Corruption Layer Responsibility

**Core does NOT translate framework types** - that's the implementation's job via Anti-Corruption Layer (ACL).

**Implementation ACL pattern:**
```java
// In io.eventbob.spring (NOT in core)
@Service
public class SpringCapabilityResolver implements CapabilityResolver {

    @Override
    public List<Endpoint> resolveEndpoints(RoutingKey key) {
        // 1. Query using Spring JDBC (framework-specific)
        List<EndpointRow> rows = jdbcTemplate.query(...);

        // 2. ACL: Translate Spring types → Core types
        return rows.stream()
            .map(this::toEndpoint) // ACL translation method
            .collect(Collectors.toList());
    }

    // ACL: Spring-specific type → Core type
    private Endpoint toEndpoint(EndpointRow row) {
        return new Endpoint(
            row.getUri(),
            row.getDeploymentVersion(),
            translateState(row.getState()) // Filter GRAY/RETIRED
        );
    }
}
```

**Core never sees:**
- `EndpointRow` (Spring JDBC type)
- `JdbcTemplate` (Spring type)
- Internal deployment states like GRAY/RETIRED

### Evolution Strategy

**When adding new abstractions to core:**

1. **Evaluate need across implementations** - Will both Spring and Dropwizard need this?
2. **Start framework-agnostic** - Design without frameworks in mind
3. **Add port, not implementation** - Core defines interface, implementations provide concrete behavior
4. **Document contract clearly** - What does this abstraction mean? What are its invariants?
5. **Update tests** - ArchUnit should verify no framework leakage

**When NOT to add to core:**

- Implementation detail only one framework needs (keep in that implementation)
- Framework-specific optimization (keep in that implementation)
- Internal state that doesn't cross boundary (GRAY/RETIRED deployment states)

---

## Package Structure

```
io.eventbob.core
├── eventrouting/                      # Event Routing Subdomain
│   ├── Event.java
│   ├── EventHandler.java
│   ├── EventHandlingRouter.java
│   ├── DecoratedEventHandler.java
│   ├── EventHandlerCapability.java
│   ├── MetadataKeys.java
│   ├── EventHandlingException.java
│   ├── HandlerNotFoundException.java
│   └── UnexpectedEventHandlingException.java
└── endpointresolution/                # Endpoint Resolution Subdomain
    ├── Capability.java
    ├── CapabilityResolver.java
    ├── RoutingKey.java
    └── Endpoint.java
```

---

## Subdomains

### Event Routing (`eventrouting`)

**Responsibility:** Route events by target to handlers. Manage event lifecycle, decoration, and exception handling.

**Key Types:**
- **Event** — Immutable event data structure (source, target, metadata, parameters, payload)
- **EventHandler** — Universal interface for event processing
- **EventHandlingRouter** — Routes events by target string to specific handlers
- **DecoratedEventHandler** — Wraps handlers with cross-cutting concerns (logging, metrics, error handling)
- **MetadataKeys** — Standard metadata vocabulary (correlation-id, method, path, trace-id, etc.)
- **Exception hierarchy** — EventHandlingException, HandlerNotFoundException, UnexpectedEventHandlingException

**Dependencies:**
- → `endpointresolution.Capability` (EventHandlerCapability annotation references Capability enum)
- No other internal dependencies

**Stability:** Instability = 1.00 (maximally unstable, depends on stable abstractions)

---

### Endpoint Resolution (`endpointresolution`)

**Responsibility:** Define the port/abstraction for resolving service capabilities to physical endpoints. Support progressive deployment states.

**Key Types:**
- **Capability** — Enum of operation types (READ, WRITE, ADMIN)
- **CapabilityResolver** — Interface (port) for resolving routing keys to endpoints
- **RoutingKey** — Immutable identifier for a service operation (service + capability + method + path)
- **Endpoint** — Logical endpoint address (e.g., "messages-service")

**Dependencies:**
- None (zero outgoing dependencies within core)

**Stability:** Instability = 0.00 (maximally stable, pure abstraction/port layer)

---

## Dependency Rules

**Enforced by:** `CoreArchitectureTest` (ArchUnit)

### Rule 1: Event Routing → Endpoint Resolution (ONE-WAY)

✅ **Allowed:** `eventrouting` can depend on `endpointresolution`
- EventHandlerCapability annotation references Capability enum

❌ **Blocked:** `endpointresolution` cannot depend on `eventrouting`
- Endpoint resolution is a stable port, routing is an unstable implementation

### Rule 2: No Cyclic Dependencies

✅ **Enforced:** All packages within core must be acyclic
- Prevents hidden coupling and ensures clean boundaries

### Rule 3: Zero External Dependencies

✅ **Enforced:** Core can only depend on:
- JDK (`java.**`)
- SLF4J (`org.slf4j.**`) — logging abstraction only

❌ **Blocked:** No frameworks, no HTTP libraries, no external dependencies

### Rule 4: Package Naming Convention

✅ **Enforced:** All classes must reside in recognized packages:
- `io.eventbob.core.eventrouting.**`
- `io.eventbob.core.endpointresolution.**`

---


## Design Rationale

### Why Two Subdomains?

**Event Routing** and **Endpoint Resolution** serve different purposes:

1. **Event Routing** is about dispatching events to handlers
   - In-process concern: which handler receives this event?
   - Uses target string for simple lookup
   - Manages exception handling and decoration

2. **Endpoint Resolution** is about finding physical locations
   - Cross-process concern: where is this capability hosted?
   - Uses capability metadata (READ/WRITE/ADMIN) + operation signature
   - Supports progressive deployment (GREEN/BLUE endpoints)

**Are they separate bounded contexts?**
No. They share ubiquitous language (no terms change meaning across boundary). They are **subdomains within ONE bounded context** (Event Routing), separated for dependency management and architectural clarity.

### Why Flatten Exceptions?

Previously exceptions were in `eventrouting/exceptions/` subpackage. They were moved into `eventrouting/` directly because:

1. **Domain language:** Exceptions describe routing failures (HandlerNotFoundException), not general resolution failures. They are part of routing vocabulary.
2. **Eliminate cycles:** Separate exceptions package created bidirectional dependency (routing → exceptions → routing for Event reference).
3. **Simplicity:** Fewer layers, cohesive domain concepts in one package.

### Why Allow eventrouting → endpointresolution?

The `EventHandlerCapability` annotation (in eventrouting) references `Capability` enum (in endpointresolution). This dependency exists because:

1. **Purpose:** The annotation declares what capabilities a handler provides (metadata for registration)
2. **Stable dependency:** eventrouting (unstable, I=1.00) depends on endpointresolution (stable, I=0.00) — correct direction
3. **Acceptable coupling:** The annotation is about registration metadata, not core routing logic

**Alternative considered:** Move EventHandlerCapability to endpointresolution. Rejected because the annotation marks EventHandlers (routing concept), not endpoints (resolution concept).

---

## Ports & Adapters

### Ports Defined by Core

**CapabilityResolver** (`endpointresolution.CapabilityResolver`)
- Resolves service capabilities to physical endpoints
- Implementation will be provided by registry module (infrastructure layer)
- Supports both single endpoint and multi-endpoint resolution (for load balancing)

**EventHandler** (`eventrouting.EventHandler`)
- Universal interface for event processing
- Implemented by:
  - Services (business logic)
  - Routers (delegation)
  - Decorators (cross-cutting concerns)
  - Adapters (transport translation)

### Adapters Implement Core Ports

**Future:** When registry module is implemented, it will provide `CapabilityResolver` implementation backed by PostgreSQL.

---

## Evolution History

### 2026-02-12: Bounded Context Reorganization

**Change:** Restructured from flat package to subdomain packages

**Before:**
```
io.eventbob.core/
  Event.java
  EventHandler.java
  EventHandlingRouter.java
  ...
  exceptions/
    EventHandlingException.java
    HandlerNotFoundException.java
```

**After:**
```
io.eventbob.core/
  eventrouting/
    Event.java
    EventHandler.java
    EventHandlingRouter.java
    EventHandlingException.java     # Flattened
    HandlerNotFoundException.java   # Flattened
  endpointresolution/
    Capability.java
    CapabilityResolver.java
    RoutingKey.java
    Endpoint.java
```

**Rationale:**
1. Explicit bounded context structure for architectural clarity
2. Enforce dependency direction via ArchUnit tests
3. Prevent cyclic dependencies
4. Prepare for registry module implementation (will depend on endpointresolution port)

**Impact:**
- All imports updated from `io.eventbob.core.*` to `io.eventbob.core.{subdomain}.*`
- ArchUnit tests enforce new boundaries
- Martin metrics improve (clean dependency direction)

---

## Testing Strategy

### ArchUnit Tests

**File:** `CoreArchitectureTest.java`

**Enforces:**
1. Dependency direction (eventrouting → endpointresolution, not reverse)
2. No cyclic dependencies
3. Zero external dependencies (except JDK, SLF4J)
4. Package naming convention

**Generates:**
- Dependency graph with Martin metrics (`docs/core_dependency_graph.md`)

### Unit Tests

**Coverage:**
- Event creation, validation, immutability
- EventHandlingRouter dispatching and error handling
- DecoratedEventHandler hook execution
- MetadataKeys usage patterns

**Pattern:** Simple stubs over mocks, infrastructure-free tests

---

## Future Extensions

### Spring Implementation Module (Implemented)

**Implemented module:** `io.eventbob.spring`

**Dependencies:**
- io.eventbob.spring → io.eventbob.core.endpointresolution (implements CapabilityResolver port)
- Spring implementation provides PostgreSQL-backed capability resolution using Spring JDBC
- Scans JARs for @EventHandlerCapability annotations and persists to database using Flyway migrations

**Note:** This is one implementation using Spring Boot. Future implementations (e.g., `io.eventbob.dropwizard`) can provide alternative implementations of the same ports.

### When Adapters are Implemented

**New modules:** `io.eventbob.adapter.http`, `io.eventbob.adapter.grpc`, etc.

**Dependencies:**
- Adapters → io.eventbob.core.eventrouting (implement EventHandler)
- Adapters will translate transport protocols to Event domain model
- Adapters will define transport-specific metadata namespaces

---

## Non-Goals

**This module does NOT:**
- Provide persistence (registry module concern)
- Provide transport adapters (adapter module concern)
- Define business domain models (service concern)
- Manage deployment lifecycle (infrastructure concern)

**This module IS:**
- Pure domain model for event routing
- Stable contract for implementation modules
- Zero-dependency, framework-agnostic foundation
