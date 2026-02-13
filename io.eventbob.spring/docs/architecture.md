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

This Spring implementation provides the EventBob macro-service infrastructure:
- **Capability-based service discovery** (JAR scanning, registration)
- **Macro tracking** (logical deployment units and their endpoints)
- **Macro-service bootstrap** (startup orchestration, configuration)
- **Persistence layer** (Spring JDBC with PostgreSQL)

It sits at the infrastructure layer, implementing ports defined by `io.eventbob.core`.

**Responsibilities:**
- Scan JARs for `@EventHandlerCapability` annotations
- Persist capability metadata to PostgreSQL
- Track macros (logical deployment units) and their endpoints
- (Future) Implement `CapabilityResolver` port for endpoint resolution

**Not responsible for:**
- Event routing logic (core domain)
- Request handling (application layer)
- Business domain operations (service layer)
- Deployment orchestration (infrastructure concern - handled by DNS, service mesh, load balancers)

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
│   io.eventbob.spring            │  CapabilityScanner, CapabilityRegistrar, MacroServiceBootstrap
│                                 │  ServiceRegistryRepository (Spring JDBC)
└─────────────────────────────────┘
              ↓
┌─────────────────────────────────┐
│   Database                      │  PostgreSQL (service_capabilities, service_macros,
│                                 │  macro_capabilities, registry_version)
└─────────────────────────────────┘
```

**Dependency direction:** Infrastructure → Core (outer depends on inner). Core never imports from infrastructure.

**Anti-Corruption Layer:** Translates Spring-specific types (JdbcTemplate, ResultSet) to core types (Capability, Endpoint). Infrastructure persistence never crosses to core.

## Component Design

### CapabilityScanner

**Responsibility:** Discover capabilities from JARs via annotation scanning.

**Pattern:** Domain Service (infrastructure-flavored)

**Key operations:**
- `scanJar(jarPath, classLoader) → List<CapabilityDescriptor>`

**Dependencies:**
- ClassGraph (JAR scanning library)
- `@EventHandlerCapability` annotation (from core)

**Data flow:**
```
JAR file → ClassGraph.scan() → Find @EventHandlerCapability annotations
         → Parse operations ("GET /path") → Build CapabilityDescriptor list
```

**Why this design:**
- Annotation scanning is infrastructure concern (ClassGraph, reflection, classloaders)
- Core defines WHAT is scanned (`@EventHandlerCapability` contract)
- Infrastructure defines HOW to scan (ClassGraph implementation)

---

### CapabilityDescriptor

**Responsibility:** In-memory representation of discovered capability metadata.

**Pattern:** Data Transfer Object

**Structure:**
```java
public final class CapabilityDescriptor {
    private final String serviceName;
    private final Capability capability;      // READ/WRITE/ADMIN
    private final int capabilityVersion;
    private final String method;              // HTTP method
    private final String pathPattern;
    private final String handlerClassName;
}
```

**Lifecycle:** Created by scanner → passed to registrar → persisted → discarded

**Why immutable:** Metadata should not change after discovery. Builder pattern enforces complete construction.

---

### CapabilityRegistrar

**Responsibility:** Register macros and their capabilities in the registry.

**Pattern:** Application Service (infrastructure layer)

**Key operations:**
- `registerMacro(request) → RegistrationResult`

**Request/Response:**
```java
public record RegistrationRequest(
    String macroName,
    String endpoint,  // Logical name
    List<CapabilityDescriptor> capabilities
) {}

public record RegistrationResult(
    UUID macroId,
    Map<String, UUID> capabilityIds
) {
    public boolean isSuccess() {
        return macroId != null && !capabilityIds.isEmpty();
    }
}
```

**Transaction boundary:** Entire registration (macro + capabilities + links) is atomic.

**Idempotency:** Re-registering same macro updates endpoint but doesn't create duplicates.

**Why this design:**
- Orchestrates repository calls (registerMacro, registerCapability, linkMacroCapability)
- Encapsulates transaction boundary
- Provides high-level registration API for bootstrap

---

### ServiceRegistryRepository

**Responsibility:** Persistence of macros, capabilities, and their relationships.

**Pattern:** Repository (Spring JDBC)

**Key operations:**
- `registerMacro(macroName, endpoint) → UUID`
- `registerCapability(descriptor) → UUID`
- `linkMacroCapability(macroId, capabilityId) → void`
- `getCurrentVersion() → long`

**Database tables:**
- `service_macros` - logical deployment units
- `service_capabilities` - capability operations
- `macro_capabilities` - junction table (which macros provide which capabilities)
- `registry_version` - cache invalidation counter

**Idempotency strategy:**
- Macros: `ON CONFLICT (macro_name) DO UPDATE SET endpoint = ...`
- Capabilities: Check existence, return existing UUID
- Links: `ON CONFLICT DO NOTHING`

**Why Spring JDBC (not JPA):**
- Explicit SQL control (no hidden queries)
- PostgreSQL-specific features (gen_random_uuid(), triggers)
- Performance (no object-relational mapping overhead)

---

### MacroServiceBootstrap

**Responsibility:** Bootstrap a macro-service on startup.

**Pattern:** Application Service (infrastructure layer)

**Key operations:**
- `bootstrap(config) → BootstrapResult`

**Configuration:**
```java
public record BootstrapConfig(
    String macroName,
    List<Path> jarPaths,
    String endpoint  // null = defaults to macroName
) {}
```

**Bootstrap flow:**
```
1. Scan JARs for capabilities (via CapabilityScanner)
2. Validate at least one capability found
3. Determine endpoint (explicit or default to macroName)
4. Register macro + capabilities (via CapabilityRegistrar)
5. Return result
```

**Why this design:**
- Separates bootstrap orchestration from registration logic
- Single entry point for macro-service initialization
- Explicit configuration record (no hidden defaults)

---

## Database Schema

### service_macros

Tracks logical deployment units.

```sql
CREATE TABLE service_macros (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  macro_name VARCHAR(255) NOT NULL UNIQUE,
  endpoint VARCHAR(500) NOT NULL,
  registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
  metadata JSONB
);
```

**Key insight:** One record per macro. Endpoint is logical name, resolved by infrastructure.

---

### service_capabilities

Tracks capability operations.

```sql
CREATE TABLE service_capabilities (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  service_name VARCHAR(255) NOT NULL,
  capability capability_type NOT NULL,
  capability_version INTEGER NOT NULL,
  method VARCHAR(10) NOT NULL,
  path_pattern VARCHAR(500) NOT NULL,
  registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
  metadata JSONB,

  CONSTRAINT uq_capability_routing_key
    UNIQUE(service_name, capability, capability_version, method, path_pattern)
);
```

**Key insight:** Routing key must be unique. One version per routing key.

---

### macro_capabilities

Junction table linking macros to capabilities.

```sql
CREATE TABLE macro_capabilities (
  macro_id UUID NOT NULL REFERENCES service_macros(id) ON DELETE CASCADE,
  capability_id UUID NOT NULL REFERENCES service_capabilities(id) ON DELETE CASCADE,
  linked_at TIMESTAMP NOT NULL DEFAULT NOW(),

  PRIMARY KEY (macro_id, capability_id)
);
```

**Key insight:** Capabilities can be shared across macros. Same capability, multiple macros.

---

### registry_version

Cache invalidation counter.

```sql
CREATE TABLE registry_version (
  id INTEGER PRIMARY KEY DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

  CONSTRAINT chk_single_row CHECK (id = 1)
);

CREATE OR REPLACE FUNCTION bump_registry_version()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE registry_version SET version = version + 1, updated_at = NOW() WHERE id = 1;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_capabilities_version
  AFTER INSERT OR UPDATE OR DELETE ON service_capabilities
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();

CREATE TRIGGER trigger_macros_version
  AFTER INSERT OR UPDATE OR DELETE ON service_macros
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();
```

**Key insight:** Any registry change bumps version. Enables efficient cache invalidation.

---

## Infrastructure Choices

### Why Spring Boot?

- Mature dependency injection (component wiring)
- Spring JDBC for explicit SQL control
- Flyway for schema migrations
- TestContainers integration for tests
- Configuration management (application.yml)

### Why PostgreSQL?

- Transactional DDL (schema migrations are atomic)
- Triggers (automatic version bumping)
- Partial unique indexes (future extensibility)
- JSONB for extensible metadata

### Why ClassGraph?

- Fast JAR scanning (no custom classloader needed)
- Annotation filtering (find @EventHandlerCapability)
- Works with modular JARs

---

## Simplification History

### V002: Removed Deployment Orchestration (2026-02-12)

**What was removed:**
- Deployment state tracking (BLUE, GREEN, GRAY, RETIRED enums)
- Instance-level tracking (instanceId, last_heartbeat, status)
- Conflict detection (version mismatch warnings)
- Deployment history (audit log)
- jarVersion tracking

**Why removed:**
- Deployment orchestration is infrastructure concern (DNS, service mesh, load balancers)
- Registry should track "what and where" (capabilities and endpoints), not "how" (deployment states)
- Over-engineered for current needs (no consumers of deployment state data)

**What changed:**
- `service_instances` → `service_macros` (tracks logical units, not physical instances)
- `instance_capabilities` → `macro_capabilities`
- Endpoint is now logical name (e.g., "messages-service"), not physical URL
- RegistrationRequest simplified from 7 fields to 3
- Database schema simplified (removed state columns, deployment history table)

**Trade-offs:**
- Simpler model (less code, less complexity)
- No runtime conflict detection (database unique constraint enforces single version per routing key)
- No audit trail (no deployment history)
- Cannot track multiple active versions of same capability (database constraint prevents it)

---

## Future Extension Points

### 1. CapabilityResolver Implementation

**Goal:** Implement `CapabilityResolver` port from core.

**Signature:**
```java
public interface CapabilityResolver {
    List<Endpoint> resolve(String routingKey);
}
```

**Implementation approach:**
- Query `service_capabilities` by routing key
- Join to `macro_capabilities` to get macros
- Join to `service_macros` to get endpoints
- Return `List<Endpoint>` with logical names
- Infrastructure (DNS, load balancers) resolves logical → physical

**Why not yet implemented:** Routing logic still in development. Resolver will be added when router is ready.

---

### 2. Health-Based Routing (Future)

**Goal:** Exclude unhealthy macros from resolution.

**Approach (if needed):**
- Add health check endpoint to macros
- Periodically query health, update macro metadata
- Filter unhealthy macros in resolver query

**Note:** This may not be needed if infrastructure (load balancers, service mesh) already handles health.

---

### 3. Multi-Region Registry (Future)

**Goal:** Replicate capabilities across regions.

**Approach:**
- Add region column to service_macros
- Replicate capability metadata across regions
- Resolver queries local region first, falls back to remote

---

## Summary

This Spring implementation provides a **simple, honest registry** for capability-based routing:

1. **Scan JARs** to discover capabilities
2. **Register macros** and their capabilities
3. **Persist to PostgreSQL** with transactional guarantees
4. **(Future) Resolve routing keys** to endpoints for the router

**Key architectural decisions:**
- PostgreSQL for transactional correctness
- Spring JDBC for explicit SQL control
- Flat package structure (currently 5 classes, will refactor if >15)
- Dependency direction: infrastructure → core (never reverse)
- Anti-Corruption Layer: Spring types never leak to core
- Logical endpoints (resolved by infrastructure) not physical URLs

**What this is NOT:**
- Not a deployment orchestration system (that's infrastructure)
- Not a health monitoring system (that's infrastructure)
- Not a service mesh (that's infrastructure)
- Just a capability registry enabling routing decisions
