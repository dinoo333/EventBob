# EventBob Spring Implementation Architecture

**Module:** io.eventbob.spring
**Type:** Infrastructure Implementation (Bridge Pattern)
**Last Updated:** 2026-02-12

## Bridge Pattern: Core + Spring

This module is the **Spring Boot implementation of EventBob**. It implements the complete macro-service infrastructure using Spring Framework.

**Architecture Pattern:** Bridge Pattern
- **Abstraction:** `io.eventbob.core` (domain model, ports, interfaces)
- **Implementation:** `io.eventbob.spring` (Spring-based concrete implementation)

This is NOT "a registry using Spring." This is **EventBob implemented using Spring**.

## Module Purpose

This Spring implementation provides the complete EventBob macro-service infrastructure:
- **Capability-based service discovery** (JAR scanning, registration, conflict detection)
- **Instance tracking and health management** (deployment states, heartbeat monitoring)
- **Macro-service bootstrap** (startup orchestration, configuration)
- **Persistence layer** (Spring JDBC with PostgreSQL)

It sits at the infrastructure layer, implementing ports defined by `io.eventbob.core`.

**Responsibilities:**
- Scan JARs for `@EventHandlerCapability` annotations
- Persist capability metadata to PostgreSQL
- Track running instances and their health
- Detect capability version conflicts
- (Future) Implement `CapabilityResolver` port for endpoint resolution

**Not responsible for:**
- Event routing logic (core domain)
- Request handling (application layer)
- Business domain operations (service layer)

## Layer Assignment

```
┌─────────────────────────────────┐
│   Application Layer             │  (future: API gateway, HTTP endpoints)
└─────────────────────────────────┘
              ↓
┌─────────────────────────────────┐
│   Domain Layer                  │
│   io.eventbob.core              │  Event, EventHandler, Capability, CapabilityResolver port
└─────────────────────────────────┘
              ↑
┌─────────────────────────────────┐
│   Infrastructure Layer          │
│   io.eventbob.spring            │  JAR scanning, JDBC, Spring Boot (Bridge Implementation)
│                                 │
│   - CapabilityScanner           │  (uses ClassGraph)
│   - CapabilityRegistrar         │  (service layer)
│   - ServiceRegistryRepository   │  (JDBC persistence)
│   - MacroServiceBootstrap       │  (startup orchestration)
└─────────────────────────────────┘
```

**Dependency direction:** Spring Implementation → Core (infrastructure depends on domain, never reverse)

## Future Implementations

The Bridge Pattern enables multiple framework-based implementations:
- `io.eventbob.spring` - Spring Boot + Spring JDBC (this module)
- `io.eventbob.dropwizard` - Dropwizard + JDBI (planned)
- `io.eventbob.micronaut` - Micronaut + Micronaut Data (future)

All implementations depend on `io.eventbob.core`. None depend on each other.

## Dependencies on Core

### Imports from io.eventbob.core

**From `io.eventbob.core.endpointresolution`:**
- `Capability` (enum: READ, WRITE, ADMIN)

**From `io.eventbob.core.eventrouting`:**
- `EventHandler` (interface)
- `EventHandlerCapability` (annotation)

**Purpose:**
- `Capability` - needed to parse capability types from annotations
- `EventHandler` - ClassGraph scans for classes implementing this interface
- `EventHandlerCapability` - annotation that declares capability metadata

### Implements (future)

**Will implement:**
- `CapabilityResolver` port (defined in core)

**Contract:**
```java
public interface CapabilityResolver {
    List<Endpoint> resolveEndpoints(String routingKey);
}
```

**Translation layer (Anti-Corruption):**
This implementation's internal `DeploymentState` (BLUE, GREEN, GRAY, RETIRED) will be translated to core's `EndpointState` (BLUE, GREEN) when implementing the resolver. GRAY and RETIRED states never cross the boundary.

**Why "Spring" not "Registry"?**

The module is named `io.eventbob.spring` because:
1. It identifies the **implementation framework** (Spring Boot)
2. It provides ALL EventBob infrastructure capabilities, not just registry
3. It enables peer implementations like `io.eventbob.dropwizard` to coexist
4. The domain abstraction lives in `io.eventbob.core`, not here

## Infrastructure Choices

### PostgreSQL (Database)

**Why PostgreSQL:**
- ACID transactions ensure instance + capabilities + linkage are atomic
- Partial unique indexes (`WHERE deployment_state = 'GREEN'`) enforce business rules at DB level
- JSONB support for future rollout policies
- Native enum types for deployment_state, instance_status, capability_type
- Mature replication for multi-region registry (future)

**Alternatives considered:**
- Redis - too ephemeral, loses state on restart
- DynamoDB - no partial unique indexes, can't enforce single GREEN rule
- Consul - service discovery focus, lacks transaction semantics

**Trade-off:** PostgreSQL requires operational overhead (backups, monitoring) but provides correctness guarantees critical for routing.

### Spring Boot JDBC (Persistence)

**Why Spring JDBC (not JPA/Hibernate):**
- Registry has complex queries (idempotent upserts, conflict detection)
- `ON CONFLICT` clauses are idiomatic in PostgreSQL but awkward in JPA
- Row mapping is simple (records map 1:1 to DTOs)
- No object-relational impedance mismatch (no lazy loading, no cascades)

**Alternatives considered:**
- JPA/Hibernate - adds complexity, hides SQL
- jOOQ - type-safe SQL but requires code generation
- Plain JDBC - verbose, no transaction management

**Trade-off:** Spring JDBC is verbose (manual row mapping) but explicit and debuggable.

### Flyway (Database Migrations)

**Why Flyway:**
- Version-controlled schema evolution
- Repeatable migrations for development
- CI integration (migration runs before tests)

**Alternatives considered:**
- Liquibase - XML overhead, less readable than SQL

### ClassGraph (JAR Scanning)

**Why ClassGraph:**
- Fast classpath scanning without loading classes
- Annotation parameter extraction without reflection
- Works with JARs (not just exploded directories)

**Alternatives considered:**
- Spring's classpath scanning - slower, requires Spring context
- Manual reflection - too low-level, error-prone

**Trade-off:** ClassGraph is a third-party dependency, but it's the de facto standard for annotation scanning.

## Internal Package Structure

**Current structure (flat):**
```
io.eventbob.spring/
├── CapabilityDescriptor.java       (value object)
├── CapabilityRegistrar.java        (service)
├── CapabilityScanner.java          (service)
├── DeploymentState.java            (enum)
├── InstanceStatus.java             (enum)
├── MacroServiceBootstrap.java      (orchestration)
└── ServiceRegistryRepository.java  (data access)
```

**Rationale for flat structure:**
- Small module (7 classes)
- No internal complexity requiring subpackages
- All classes are cohesive (capability registration domain)

**Future subpackage structure (when needed):**
```
io.eventbob.spring/
├── api/                  (REST endpoints for registry queries)
├── domain/               (CapabilityDescriptor, DeploymentState, InstanceStatus)
├── repository/           (ServiceRegistryRepository, row mappers)
├── scanner/              (CapabilityScanner, JAR introspection)
└── service/              (CapabilityRegistrar, MacroServiceBootstrap)
```

**Trigger for subpackaging:** When module exceeds 15 classes or when boundary tests (ArchUnit) detect cycles in flat structure.

## Architectural Boundaries

### No Dependency on Other Implementation Modules

**Enforced by:** `RegistryArchitectureTest.registryShouldNotDependOnOtherModules()`

**Rule:** Registry must not depend on:
- `io.eventbob.api` (future HTTP API module)
- `io.eventbob.gateway` (future gateway module)
- `io.eventbob.service` (future service implementation module)

**Rationale:** Registry is infrastructure. It depends only on core domain and third-party libraries. Coupling to other implementation modules would create cycles.

### Acyclic Package Dependencies

**Enforced by:** `RegistryArchitectureTest.noCyclicDependencies()`

**Rule:** If subpackages are introduced, they must be acyclic.

**Current status:** Flat structure (no subpackages) - test passes with `allowEmptyShould(true)`.

### Dependency on Infrastructure Only

**Allowed dependencies:**
- Spring Boot (JDBC, transactions, dependency injection)
- PostgreSQL driver (JDBC connectivity)
- Flyway (migrations)
- ClassGraph (JAR scanning)
- SLF4J (logging)

**Forbidden dependencies:**
- GUI frameworks (Swing, JavaFX)
- Alternative databases (MongoDB, Cassandra) without abstraction
- Alternative web frameworks (Dropwizard, Micronaut) without abstraction

**Enforcement:** ArchUnit test verifies registry only depends on whitelisted packages.

## Future Extension Points

### 1. Implement CapabilityResolver Port

**Current state:** Registry stores capabilities and instances but doesn't expose resolution logic to core.

**Future state:** Registry implements `CapabilityResolver` port:
```java
@Service
public class DatabaseCapabilityResolver implements CapabilityResolver {
    @Override
    public List<Endpoint> resolveEndpoints(String routingKey) {
        // Query service_capabilities JOIN instance_capabilities
        // Filter by deployment_state = GREEN
        // Filter by instance_status = HEALTHY
        // Return list of endpoints
    }
}
```

**When:** After Event Routing context defines the `CapabilityResolver` port contract.

### 2. Progressive Rollout Logic

**Current state:** Deployment states (BLUE, GREEN) exist but traffic splitting is not implemented.

**Future state:**
- `rollout_policy` JSONB column stores canary percentages
- `CapabilityResolver` returns weighted endpoints (e.g., 90% GREEN, 10% BLUE)
- Automatic rollback on error rate threshold

**Schema ready:** `rollout_policy` column exists but unused.

### 3. Health-Based Routing

**Current state:** Instance status (HEALTHY, UNHEALTHY) tracked but not used in resolution.

**Future state:**
- `CapabilityResolver` excludes UNHEALTHY instances from results
- DRAINING instances receive no new requests (graceful shutdown)

**When:** After health check service is implemented.

### 4. Multi-Region Registry

**Current state:** Single PostgreSQL database.

**Future state:**
- PostgreSQL logical replication to regional replicas
- Read replicas for endpoint resolution (local latency)
- Write leader for registration (consistency)

**Trade-off:** Eventual consistency between regions vs. strong consistency in single region.

## Known Technical Debt

### 1. Multiple Operations Handling (FIXED in this commit)

**Previous state:** `CapabilityScanner` silently dropped all but first operation when a handler declared multiple operations.

**Current state:** Scanner creates one descriptor per operation and returns a list.

**Rationale:** Handlers can legitimately declare multiple operations (e.g., GET /item, GET /item/{id}).

### 2. Unused Schema Columns

**Columns defined but not used:**
- `rollout_policy` JSONB (future: canary percentages, rollback rules)
- `became_blue_at`, `became_green_at`, `became_gray_at`, `retired_at` (future: state transition timestamps)

**Decision:** Keep columns in V001 migration (future-proofing) OR remove them and re-add in V002 when rollout logic is implemented.

**Current stance:** Keep columns. Migration rollback is complex; adding columns later requires downtime.

### 3. No Interface for CapabilityScanner

**Current state:** `CapabilityScanner` is a concrete class with hardcoded `URLClassLoader`.

**Future state:** Extract `CapabilityScanner` interface when testing becomes difficult or when multiple scanner implementations are needed (e.g., Maven plugin scanner for build-time validation).

**When:** After first pain point (hard to test, need alternative implementation).

### 4. TODOs in Code

**`CapabilityRegistrar` line 150:**
```java
// TODO: Emit metric for conflict detection
```

**Decision:** Either emit the metric or remove the comment. TODOs in shipped code are debt.

**Action:** Will emit metric in future observability pass.

## Deployment State Model Design

### Why BLUE/GREEN/GRAY/RETIRED?

**Industry standard patterns:**
- Blue/Green deployment - two versions, instant cutover
- Canary deployment - gradual rollout with percentage split

**EventBob model combines both:**
- **BLUE** = canary version (progressive rollout 0% → 100%)
- **GREEN** = production version (100% traffic)
- **GRAY** = superseded or failed version (no traffic, awaiting cleanup)
- **RETIRED** = cleaned up (eventual deletion)

**Why not just BLUE/GREEN?**
- Need to track superseded versions for audit (can't delete GREEN immediately when BLUE becomes new GREEN)
- Need to track failed rollouts separately from successful versions (GRAY vs RETIRED)

**Why not RED/GREEN/BLUE?**
- RED implies error state, but GRAY versions might be healthy (just superseded)
- Industry uses "blue/green" terminology already

**Alternative considered:** ACTIVE, CANARY, DEPRECATED, DELETED
- More explicit but less familiar to operators
- "Active" is ambiguous (is CANARY active?)

## Martin Metrics (Package Health)

**Current state (flat package):**
```
io.eventbob.spring:
  A (Abstractedness) = 0.14  (1 interface, 6 concrete classes)
  I (Instability) = 1.0      (depends on core, nothing depends on it)
  D (Distance) = 0.14        (close to ideal: concrete + unstable)
```

**Interpretation:**
- Low abstractedness is correct (infrastructure is concrete)
- High instability is correct (outer layer should be unstable)
- Distance of 0.14 is healthy (far from Zone of Pain)

**When to refactor:**
- If Distance > 0.5, consider extracting abstractions
- If new modules depend on registry, Instability should decrease

## ArchUnit Enforcement

**Tests in `RegistryArchitectureTest`:**

1. **Registry can depend on Core**
   - Verifies registry only imports from allowed packages (core, Spring, PostgreSQL, etc.)
   - Prevents accidental dependencies on GUI libraries, alternative frameworks

2. **Registry should not depend on other implementation modules**
   - Prevents cycles with future API, gateway, service modules
   - Keeps registry as pure infrastructure

3. **Packages should be acyclic**
   - Currently passes (flat structure)
   - Will enforce DAG when subpackages are introduced

4. **Generate dependency graph**
   - Auto-generates `docs/registry_dependency_graph.md`
   - Run test to update after structural changes

## Testing Strategy

See `implementation_notes.md` for detailed test patterns.

**Architecture test coverage:**
- Dependency direction enforced
- Cycle detection enforced
- Allowed dependency whitelist enforced
- Martin metrics tracked

## Summary

The registry module is **infrastructure** that serves the **Event Routing core domain** by:
1. Scanning JARs for capabilities (ClassGraph)
2. Persisting to PostgreSQL (Spring JDBC, Flyway)
3. Tracking instances and health (JDBC repository)
4. Enforcing business rules at DB level (unique indexes for BLUE/GREEN)
5. (Future) Implementing CapabilityResolver port for endpoint resolution

**Key architectural decisions:**
- PostgreSQL for transactional correctness
- Spring JDBC for explicit SQL control
- Flat package structure (will refactor when >15 classes)
- Dependency direction: registry → core (never reverse)
- Anti-Corruption Layer: internal DeploymentState → external EndpointState

**Known debt:**
- Unused schema columns (future rollout logic)
- No scanner interface (will extract when needed)
- TODOs in code (emit metrics, remove comments)
