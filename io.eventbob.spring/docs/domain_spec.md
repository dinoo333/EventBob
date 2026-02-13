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
- **Tracking service instances and their health**
- **Managing deployment states** (blue/green deployments)
- **Persisting capability metadata** to PostgreSQL
- **Answering:** "Which physical endpoints can handle this capability operation?"

**Not responsible for:** Event routing logic (that's in core domain), request handling (that's in adapters), business domain operations.

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
│  - Instance lifecycle       │
│  - Deployment states        │
│  - Spring JDBC persistence  │
└─────────────────────────────┘

Note: Future implementations (io.eventbob.dropwizard, etc.) will provide
the same capabilities using different frameworks.
```

**Anti-Corruption Layer:**
This implementation translates its internal deployment model to the core's routing model:
- Internal: `DeploymentState` (BLUE, GREEN, GRAY, RETIRED)
- External: `EndpointState` (BLUE, GREEN) exposed to core via CapabilityResolver

The GRAY and RETIRED states never cross the boundary.

## Domain Concepts

### Macro-Service

A **macro-service** is a logical deployment unit that bundles multiple service JARs into a single runtime process.

**Properties:**
- Has a unique name (e.g., "messages-readonly-macro")
- Contains one or more service JARs (e.g., messages.jar, notifications.jar)
- Can have multiple running instances (e.g., pod-1, pod-2, pod-3)
- Owns a set of declared capabilities (discovered via JAR scanning)

**Relationship to services:**
- A macro contains services (composition)
- A service declares capabilities (annotation)
- A capability is an operation (method + path + capability type)

### Instance

An **instance** is a single running process of a macro-service.

**Identity:** `(macroName, instanceId)` uniquely identifies an instance
- `macroName` = logical deployment unit (e.g., "messages-readonly-macro")
- `instanceId` = physical process identifier (e.g., "pod-1", UUID)

**Properties:**
- `endpoint` - physical network address (scheme://host:port)
- `status` - health state (HEALTHY, UNHEALTHY, DRAINING, TERMINATED)
- `heartbeatAt` - last liveness signal
- `deploymentVersion` - monotonic version number for this macro

**Lifecycle:**
1. Instance starts → registers with registry
2. Instance declares capabilities (via JAR scan)
3. Instance sends heartbeats
4. Instance drains or terminates → status updated

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

### Deployment State

The **rollout lifecycle phase** of a capability operation.

**States:**
- **BLUE** - New version being progressively rolled out (0% → 100% traffic)
- **GREEN** - Stable production version (100% traffic)
- **GRAY** - Superseded or failed version (no traffic, awaiting cleanup)
- **RETIRED** - Cleaned up, no longer queryable

**State transitions (business rules):**
```
BLUE → GREEN   (rollout completes successfully)
BLUE → GRAY    (rollout fails or is manually aborted)
GREEN → GRAY   (superseded by new GREEN)
GRAY → RETIRED (cleanup after grace period)
```

**Invariants:**
- Only ONE GREEN deployment per routing key (enforced by unique index)
- Only ONE BLUE deployment per routing key (enforced by unique index)
- GRAY deployments can have multiple versions (historical record)
- RETIRED deployments are eventually deleted (lifecycle management)

**Transition enforcement:** Currently **operator-controlled** (manual state updates). Transitions are not programmatically enforced. If transitions become business-critical, a `DeploymentStateTransition` service should enforce the rules.

### Instance Status

The **health state** of a running instance.

**States:**
- **HEALTHY** - Passing health checks, receiving traffic
- **UNHEALTHY** - Failing health checks, removed from routing
- **DRAINING** - Graceful shutdown in progress (no new requests)
- **TERMINATED** - Process has exited

**Lifecycle:**
```
HEALTHY ↔ UNHEALTHY  (health checks)
HEALTHY → DRAINING → TERMINATED  (graceful shutdown)
UNHEALTHY → TERMINATED  (forced shutdown)
```

## Domain Invariants

### 1. Idempotent Registration

**Rule:** Registering the same instance with the same capabilities multiple times has no side effects.

**Implementation:**
- Instance registration uses `ON CONFLICT (macro_name, instance_id) DO UPDATE`
- Capability registration uses `ON CONFLICT (routing_key, deployment_state) DO UPDATE`
- Re-registration updates `heartbeat_at` but does not create duplicates

**Rationale:** Macro-services may restart and re-register. The registry must not accumulate garbage.

### 2. Conflict Detection

**Rule:** Registering a capability with a version that differs from the current GREEN deployment triggers a conflict warning.

**Business meaning:** If service A declares "messages:READ:GET:/content v2" but the registry has "messages:READ:GET:/content v1" as GREEN, this is a **deployment conflict**. The operator must decide:
- Is v2 a new rollout (promote to BLUE)?
- Is v1 still canonical (reject v2)?

**Implementation:** `detectConflicts()` query joins new capabilities against existing GREEN deployments where version mismatches.

### 3. Single GREEN Rule

**Rule:** Only one deployment of a capability can be GREEN at any time.

**Enforcement:** PostgreSQL unique index:
```sql
CREATE UNIQUE INDEX idx_single_green_capability
ON service_capabilities (routing_key)
WHERE deployment_state = 'GREEN';
```

**Rationale:** GREEN means "production version receiving 100% traffic." Two GREEN versions would create routing ambiguity.

### 4. Single BLUE Rule

**Rule:** Only one BLUE deployment per capability can exist during rollout.

**Enforcement:** PostgreSQL unique index:
```sql
CREATE UNIQUE INDEX idx_single_blue_capability
ON service_capabilities (routing_key)
WHERE deployment_state = 'BLUE';
```

**Rationale:** BLUE means "version being progressively rolled out." Two concurrent rollouts would conflict.

### 5. Instance-Capability Linkage

**Rule:** An instance can only be linked to capabilities it actually provides.

**Validation:** During registration, only capabilities discovered via JAR scanning for that macro are linked to instances of that macro.

**Enforcement:** Application logic in `CapabilityRegistrar` (not database constraint, since capabilities can outlive instances).

## Ubiquitous Language

| Term | Meaning | Example |
|------|---------|---------|
| **Macro-service** | Logical deployment unit bundling multiple service JARs | "messages-readonly-macro" |
| **Instance** | Single running process of a macro-service | "pod-1" at "http://10.0.1.5:8080" |
| **Capability** | Typed operation a service can perform | READ, WRITE, ADMIN |
| **Routing Key** | Unique identifier for a capability operation | "messages:READ:GET:/content" |
| **Capability Version** | Contract version for an operation | v1, v2, v3 |
| **Deployment State** | Rollout lifecycle phase | BLUE, GREEN, GRAY, RETIRED |
| **Instance Status** | Health state of a running instance | HEALTHY, UNHEALTHY, DRAINING, TERMINATED |
| **Capability Descriptor** | Discovered handler metadata | Parsed from @EventHandlerCapability |
| **JAR Scanning** | Capability discovery mechanism | ClassGraph reads annotations |
| **Conflict** | Version mismatch between declared and registered capability | v2 declared but v1 is GREEN |
| **Rollout** | Progressive traffic shift from GREEN to BLUE | 0% → 100% |
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

**Responsibility:** Register service instances and their capabilities with conflict detection.

**Operations:**
- `registerInstance(request)` - Persist instance and link to capabilities
- `detectConflicts()` - Check for version mismatches with GREEN deployments
- `recordDeployment()` - Audit log entry for registration event

**Domain logic:**
- Idempotent registration (updates on conflict)
- Conflict detection before linkage
- Transactional (instance + capabilities + linkage + audit)

**Not responsible for:** JAR scanning (that's the scanner's job), routing (that's core's job).

## Context Boundary Contract

**Imports from Event Routing Context:**
- `io.eventbob.core.endpointresolution.Capability` (enum)
- `io.eventbob.core.eventrouting.EventHandler` (interface)
- `io.eventbob.core.eventrouting.EventHandlerCapability` (annotation)

**Exports to Event Routing Context (future):**
- Implementation of `CapabilityResolver` port (not yet implemented)
- Returns `List<Endpoint>` for a given routing key
- Translates `DeploymentState.BLUE/GREEN` → `EndpointState.BLUE/GREEN`

**Boundary crossing rules:**
- Registry can import from core (outer depends on inner)
- Core cannot import from registry (inner never depends on outer)
- Registry's internal model (GRAY, RETIRED states) never crosses to core

## Future Evolution

**Planned extensions:**
1. Implement `CapabilityResolver` port (provide endpoint resolution to core)
2. Progressive rollout logic (traffic splitting between BLUE and GREEN)
3. Health-based routing (exclude UNHEALTHY instances from resolution)
4. Rollout policy (canary percentages, rollback triggers)
5. Multi-region registry (capability replication across data centers)

## Summary

The Service Registry is a **supporting subdomain** that enables capability-based routing by:
1. Discovering capabilities via JAR scanning
2. Registering instances and linking them to capabilities
3. Tracking deployment states (BLUE/GREEN/GRAY/RETIRED)
4. Detecting conflicts (version mismatches)
5. Providing endpoint resolution to the Event Routing core

**Key insight:** The registry owns the "where" (which instances), not the "how" (routing logic). It translates physical topology (instances, versions, health) into routing decisions for the core domain.
