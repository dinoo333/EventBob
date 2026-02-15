# EventBob Spring Implementation - Domain Specification

**Module:** io.eventbob.spring (Bridge Implementation)
**Implements:** Service capability registration and discovery infrastructure
**Type:** Infrastructure layer (Bridge Pattern)
**Serves:** Event Routing Core Domain
**Last Updated:** 2026-02-12

## Implementation Overview

This module is the **Spring Boot implementation of EventBob infrastructure**. It provides concrete implementations of the ports and interfaces defined in `io.eventbob.core`.

**Key Point:** This is NOT a bounded context itself. The bounded contexts are defined in `io.eventbob.core`. This module is one IMPLEMENTATION of the infrastructure that supports those contexts.

## What This Implementation Provides

This Spring-based implementation is responsible for:
- **Discovering and registering service capabilities** via JAR scanning
- **Tracking macroliths (logical deployment units)** and their endpoints
- **Persisting capability metadata** to PostgreSQL
- **Answering:** "Which endpoints can handle this capability operation?"

**Not responsible for:** Event routing logic (that's in core domain), request handling (that's in adapters), business domain operations, deployment orchestration (that's infrastructure concern).

## Bounded Context Relationships

```
┌─────────────────────────────┐
│   Event Routing Context     │  Core Domain
│   (io.eventbob.core)        │
│                             │
│  Defines ports:             │
│  - EventHandler             │
│  - EventHandlerCapability   │
│  - Capability enum          │
│  - CapabilityResolver       │
│  - Endpoint                 │
└─────────────────────────────┘
              ↑
              │ implements ports
              │ (Anti-Corruption Layer)
              │
┌─────────────────────────────┐
│  Spring Implementation      │  Infrastructure (Bridge)
│  (io.eventbob.spring)       │
│                             │
│  Implements:                │
│  - CapabilityResolver port  │
│    (future)                 │
│                             │
│  Provides:                  │
│  - JAR scanning             │
│  - Capability registration  │
│  - Macrolith tracking           │
│  - Spring JDBC persistence  │
└─────────────────────────────┘

Note: Future implementations (io.eventbob.dropwizard, etc.) will provide
the same capabilities using different frameworks.
```

## Domain Concepts

### Macrolith-Service (Macro)

A **macrolith** is a logical deployment unit that bundles multiple service JARs into a single runtime process.

**Properties:**
- Has a unique name (e.g., "messages-service")
- Contains one or more service JARs (e.g., messages.jar, notifications.jar)
- Has a logical endpoint (typically the macrolith name itself, resolved by infrastructure)
- Owns a set of declared capabilities (discovered via JAR scanning)

**Relationship to services:**
- A macrolith contains services (composition)
- A service declares capabilities (annotation)
- A capability is an operation (method + path + capability type)

**Infrastructure Resolution:**
Macroliths are resolved to physical addresses by infrastructure (DNS, service mesh, load balancers). The registry stores logical macrolith names (e.g., "messages-service"), not physical URLs.

### Capability

A **capability** is a typed operation that a service can perform.

**Type hierarchy (from Event Routing context):**
- `READ` - Query operations (GET, safe and idempotent)
- `WRITE` - Mutation operations (POST, PUT, DELETE)
- `ADMIN` - Administrative operations (privileged access)

**Capability operation identity:**
```
routingKey = serviceName:capability:method:pathPattern
Example: "messages:READ:GET:/content"
```

**Versioning:**
Each capability has a `capabilityVersion` (integer). When the contract changes (request/response schema, semantics), the version increments.

### Capability Descriptor

An **in-memory representation** of a discovered capability operation.

**Source:** Parsed from `@EventHandlerCapability` annotation during JAR scanning

**Contents:**
- Service name (which service provides this)
- Capability type (READ/WRITE/ADMIN)
- Capability version (contract version)
- HTTP method (GET, POST, etc.)
- Path pattern (routing template)
- Handler class name (which Java class implements this)

**Lifecycle:** Created during bootstrap → validated → persisted → discarded (ephemeral)

### Endpoint

A **logical address** where events can be routed.

**Model:** Simple string wrapper (e.g., "messages-service")

**Resolution:** Infrastructure (DNS, service mesh, load balancers) resolves logical names to physical addresses. The routing layer doesn't need to know about IPs, ports, or protocols.

**Transport-agnostic:** Endpoint names don't specify protocol (http, grpc, etc.). Infrastructure handles protocol selection.

## Domain Invariants

### 1. Idempotent Registration

**Rule:** Registering the same macrolith with the same capabilities multiple times has no side effects.

**Implementation:**
- Macrolith registration uses `ON CONFLICT (macrolith_name) DO UPDATE` (last-wins)
- Capability registration uses `ON CONFLICT DO NOTHING` (first-wins)
- Link registration uses `ON CONFLICT DO NOTHING` (first-wins)
- Re-registration of macroliths updates endpoint but does not create duplicates
- Re-registration of capabilities reuses existing capability UUID

**Rationale:** Macroliths may restart and re-register (endpoints may change). Capabilities are immutable once registered (first registration wins). The registry must not accumulate garbage.

### 2. Unique Capability Routing Key

**Rule:** Each capability operation is uniquely identified by its routing key (service + capability + method + path + version).

**Enforcement:** PostgreSQL unique constraint on `(service_name, capability, capability_version, method, path_pattern)`

**Rationale:** Routing keys must be unambiguous. Two different capabilities cannot have the same routing key.

### 3. Macro-Capability Linkage

**Rule:** A macrolith can only be linked to capabilities it actually provides.

**Validation:** During registration, only capabilities discovered via JAR scanning for that macrolith are linked to that macrolith.

**Enforcement:** Application logic in `CapabilityRegistrar` (not database constraint, since capabilities can outlive macroliths).

## Ubiquitous Language

| Term | Meaning | Example |
|------|---------|---------|
| **Macro** or **Macro-service** | Logical deployment unit bundling multiple service JARs | "messages-service" |
| **Endpoint** | Logical address where events can be routed | "messages-service" (resolved by infrastructure) |
| **Capability** | Typed operation a service can perform | READ, WRITE, ADMIN |
| **Routing Key** | Unique identifier for a capability operation | "messages:READ:GET:/content" |
| **Capability Version** | Contract version for an operation | v1, v2, v3 |
| **Capability Descriptor** | Discovered handler metadata | Parsed from @EventHandlerCapability |
| **JAR Scanning** | Capability discovery mechanism | ClassGraph reads annotations |
| **Bootstrap** | Macro-service startup process | Scan JARs → register → start router |

## Domain Services

### CapabilityScanner

**Responsibility:** Discover capability metadata from JARs.

**Operation:** Reads `@EventHandlerCapability` annotations via ClassGraph, parses into `CapabilityDescriptor` objects.

**Domain logic:**
- Validates annotation fields (service name, capability, operations)
- Parses operation strings (format: "METHOD /path")
- Creates one descriptor per operation

**Not responsible for:** Persistence (that's the repository's job).

### CapabilityRegistrar

**Responsibility:** Register macroliths and their capabilities.

**Operations:**
- `registerMacrolith(request)` - Persist macrolith and link to capabilities

**Domain logic:**
- Idempotent registration (updates on conflict)
- Transactional (macrolith + capabilities + linkage)

**Not responsible for:** JAR scanning (that's the scanner's job), routing (that's core's job), deployment orchestration (that's infrastructure's job).

### MacroServiceBootstrap

**Responsibility:** Bootstrap a macro-service on startup.

**Operations:**
- Scan JARs for capabilities
- Register macrolith and capabilities
- Return bootstrap result

**Domain logic:**
- Validates that JARs contain at least one capability
- Defaults endpoint to macrolith name if not specified

## Context Boundary Contract

**Imports from Event Routing Context:**
- `io.eventbob.core.endpointresolution.Capability` (enum)
- `io.eventbob.core.endpointresolution.Endpoint` (value object)
- `io.eventbob.core.eventrouting.EventHandler` (interface)
- `io.eventbob.core.eventrouting.EventHandlerCapability` (annotation)

**Exports to Event Routing Context (future):**
- Implementation of `CapabilityResolver` port (not yet implemented)
- Returns `List<Endpoint>` for a given routing key

**Boundary crossing rules:**
- Registry can import from core (outer depends on inner)
- Core cannot import from registry (inner never depends on outer)

## Future Evolution

**Planned extensions:**
1. Implement `CapabilityResolver` port (provide endpoint resolution to core)
2. Health-based routing (exclude unhealthy macroliths from resolution)
3. Multi-region registry (capability replication across data centers)

## Summary

The Service Registry is a **supporting subdomain** that enables capability-based routing by:
1. Discovering capabilities via JAR scanning
2. Registering macroliths and linking them to capabilities
3. Providing endpoint resolution to the Event Routing core

**Key insight:** The registry owns the "what and where" (which macroliths provide which capabilities), not the "how" (routing logic) or deployment orchestration (infrastructure concern). It translates capability declarations into routing decisions for the core domain.
