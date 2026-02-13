# Service Registry Implementation Notes

**Module:** io.eventbob.spring
**Last Updated:** 2026-02-12

## Data Structures

### Three-Table Model

**Design rationale:** Separate tables for capabilities, macros, and their many-to-many relationship.

```sql
service_capabilities       -- What operations exist (independent of macros)
macro_capabilities         -- Join table (which macros provide which capabilities)
service_macros             -- Which logical deployment units exist
```

**Why not a single table?**
- Capabilities can exist before macros register (pre-declared capabilities)
- Macros can provide multiple capabilities (composition)
- Capabilities can be provided by multiple macros (replication)

**Why not denormalize?**
- Denormalized: `{macro_id, capability_1, capability_2, ...}` creates special cases (NULL capability columns, dynamic schema)
- Normalized: `macro_capabilities` join table makes multiple macros sharing capabilities natural, not an edge case

### Idempotency via ON CONFLICT

**Pattern:** All inserts use `ON CONFLICT (...) DO UPDATE SET ...` for idempotent registration.

**Example (macro registration):**
```sql
INSERT INTO service_macros (macro_name, endpoint)
VALUES (?, ?)
ON CONFLICT (macro_name)
DO UPDATE SET endpoint = EXCLUDED.endpoint;
```

**Rationale:**
- Macros may restart and re-register (Kubernetes pod restarts, rolling updates)
- Re-registration should update endpoint, not create duplicates
- Idempotency makes retry logic simple (just re-register)

**Trade-off:** `ON CONFLICT` is PostgreSQL-specific. Portability to MySQL requires `ON DUPLICATE KEY UPDATE`. Portability to H2 requires `MERGE`.

### Capability Uniqueness Constraint

**Constraint:** Each routing key (service + capability + version + method + path) must be unique.

```sql
CONSTRAINT uq_capability_routing_key
  UNIQUE(service_name, capability, capability_version, method, path_pattern)
```

**Rationale:**
- Routing keys must be unambiguous
- Two capabilities cannot have identical routing signatures
- Database enforces this invariant (fail-fast on violations)

**Trade-off:** Cannot have multiple active versions of same capability. If v1 and v2 exist, they must have different routing keys (different path or method).

---

## JAR Scanning

### ClassGraph Library

**Why ClassGraph?**
- Fast scanning (indexes JARs, doesn't load classes unnecessarily)
- Annotation filtering (find classes with `@EventHandlerCapability`)
- Works with nested JARs, modular JARs, and classpath directories

**Scanning process:**
```java
try (ScanResult result = new ClassGraph()
    .overrideClassLoaders(parentClassLoader)
    .acceptJars(jarPath.getFileName().toString())
    .enableAnnotationInfo()
    .scan()) {

    ClassInfoList handlers = result.getClassesWithAnnotation(EventHandlerCapability.class);
    // Parse annotations, build CapabilityDescriptor list
}
```

**Performance:** Scanning a 50MB JAR takes ~200ms. Scanning is done once at bootstrap, not per-request.

### Annotation Parsing

**Format:** `@EventHandlerCapability(operations = {"GET /users", "POST /users/{id}"})`

**Parsing logic:**
1. Split operation string on first space: `"GET /users"` → `["GET", "/users"]`
2. Validate method (must be HTTP verb)
3. Validate path (must start with `/`)
4. Create one `CapabilityDescriptor` per operation

**Error handling:** Invalid operations (no space, invalid method, invalid path) are logged and skipped. JAR scanning continues.

---

## Transaction Boundaries

### Bootstrap Transaction

**Scope:** Entire registration (macro + capabilities + links) is atomic.

```java
@Transactional
public RegistrationResult registerMacro(RegistrationRequest request) {
    UUID macroId = repository.registerMacro(request.macroName(), request.endpoint());

    for (CapabilityDescriptor cap : request.capabilities()) {
        UUID capId = repository.registerCapability(cap);
        repository.linkMacroCapability(macroId, capId);
    }

    return new RegistrationResult(macroId, capabilityIds);
}
```

**Why atomic?**
- Partial registration (macro exists but capabilities missing) creates inconsistent state
- Rollback on failure ensures all-or-nothing semantics
- Retry after failure is safe (idempotent operations)

**Transaction isolation:** `READ COMMITTED` (Spring default). Higher isolation not needed (no concurrent modifications to same macro during bootstrap).

### Repository Methods

**Pattern:** Each repository method is `@Transactional` for granular transactions.

**Rationale:**
- Repository methods may be called outside of service layer
- Explicit transaction boundary at repository level ensures correctness
- Service layer (`CapabilityRegistrar`) can coordinate multiple repository calls in one transaction

---

## Cache Invalidation

### Registry Version Counter

**Mechanism:** Trigger increments version on every table mutation.

```sql
CREATE TRIGGER trigger_capabilities_version
  AFTER INSERT OR UPDATE OR DELETE ON service_capabilities
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();

CREATE TRIGGER trigger_macros_version
  AFTER INSERT OR UPDATE OR DELETE ON service_macros
  FOR EACH STATEMENT EXECUTE FUNCTION bump_registry_version();
```

**Usage:**
```java
long currentVersion = repository.getCurrentVersion();
// Cache capabilities locally
// ...
// Periodically check version
if (repository.getCurrentVersion() != currentVersion) {
    // Invalidate cache, reload
}
```

**Why version counter, not timestamps?**
- Version is monotonic (never decreases)
- Single atomic read (no race conditions)
- Works across multiple tables (one version for entire registry)

**Trade-off:** Version bumps on EVERY mutation, even if irrelevant to cache. Fine-grained invalidation would require tracking which capabilities changed.

---

## Spring JDBC vs JPA

### Why Spring JDBC?

**Reasons:**
1. **Explicit SQL** - Full control over queries, no hidden SELECT N+1 problems
2. **PostgreSQL features** - Can use `gen_random_uuid()`, triggers, partial unique indexes
3. **Performance** - No object-relational mapping overhead
4. **Simplicity** - No entity lifecycle management, detached entities, merge conflicts

**Trade-offs:**
- More boilerplate (manual result mapping)
- No lazy loading (but we don't need it - simple data model)
- No caching (but we have custom cache invalidation strategy)

### Result Mapping

**Pattern:** Lambda-based row mapper for each query.

```java
UUID macroId = jdbc.queryForObject(
    "SELECT id FROM service_macros WHERE macro_name = ?",
    (rs, rowNum) -> (UUID) rs.getObject("id"),
    macroName
);
```

**Why lambda?** Type-safe, concise, no external mapper classes.

---

## Testing Strategy

### Integration Tests with TestContainers

**Setup:**
```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
    .withDatabaseName("eventbob_test")
    .withUsername("test")
    .withPassword("test");
```

**Why TestContainers?**
- Real PostgreSQL instance (not H2 in-memory mock)
- Migrations run against real schema
- Tests verify actual SQL (PostgreSQL-specific features like triggers)

**Test cleanup:**
```java
@BeforeEach
void cleanDatabase() {
    jdbc.execute("TRUNCATE service_capabilities, service_macros, macro_capabilities CASCADE");
    jdbc.execute("UPDATE registry_version SET version = 1");
}
```

**Why TRUNCATE, not DELETE?** Faster (no row-by-row deletion), resets sequences, honors CASCADE.

### Test Coverage

**What's tested:**
- Macro registration (create, update)
- Capability registration (create, idempotency)
- Macro-capability linkage
- Multiple macros sharing same capabilities
- Registry version bumping

**What's NOT tested:**
- JAR scanning (CapabilityScanner not yet implemented with tests)
- Concurrent registration (requires thread-safety tests)
- Database failure scenarios (requires fault injection)

---

## Bridge Pattern Implementation

### Anti-Corruption Layer

**Boundary:** Spring types stay in infrastructure layer, core types stay in core.

**Example:**
```java
// Infrastructure layer (io.eventbob.spring)
@Repository
public class ServiceRegistryRepository {
    private final JdbcTemplate jdbc;  // Spring type

    public UUID registerCapability(CapabilityDescriptor descriptor) {
        // Use Spring JDBC internally
        return jdbc.queryForObject(...);
    }
}

// Core layer (io.eventbob.core)
public interface CapabilityResolver {
    List<Endpoint> resolve(String routingKey);  // Core types only
}
```

**Violation example (NOT allowed):**
```java
// WRONG: Core importing from infrastructure
package io.eventbob.core;

import org.springframework.jdbc.core.JdbcTemplate;  // BOUNDARY VIOLATION

public class BadResolver {
    private final JdbcTemplate jdbc;  // Spring type leaked to core
}
```

**Enforcement:** ArchUnit tests verify no core → infrastructure dependencies.

---

## Simplification History

### V001: Initial Schema (Deployment Orchestration)

**What was included:**
- Deployment states (BLUE, GREEN, GRAY, RETIRED)
- Instance tracking (instanceId, last_heartbeat, status)
- Conflict detection (version mismatch warnings)
- Deployment history (audit log)

**Why included?** Anticipated need for progressive rollouts (blue/green deployments).

**Problem:** Over-engineered for current needs. No consumers of deployment state data.

---

### V002: Removed Deployment Orchestration (Current)

**What was removed:**
- `deployment_state` enum
- `instance_status` enum
- Deployment state columns from `service_capabilities`
- Instance tracking columns from `service_instances` (renamed to `service_macros`)
- `deployment_history` table
- `capability_conflicts` view
- Conflict detection logic in application code

**Why removed:**
- Deployment orchestration is infrastructure concern (DNS, service mesh, load balancers handle it)
- Registry should track "what and where" (capabilities and endpoints), not "how" (deployment states)
- Simpler model, less code, easier to understand

**Impact:**
- Cannot track multiple active versions of same capability (database constraint enforces uniqueness)
- No runtime conflict detection (database errors on duplicate routing keys)
- No audit trail (no deployment history)

**Trade-off:** Simpler model at cost of features. If these features are needed later, they can be re-added.

---

## Performance Characteristics

### Bootstrap Performance

**Measured:** Registering 50 capabilities takes ~150ms (local PostgreSQL).

**Breakdown:**
- JAR scanning: ~100ms (ClassGraph)
- Registration: ~50ms (50 INSERT operations in single transaction)

**Bottleneck:** JAR scanning (file I/O). Registration is fast (database inserts are batched in transaction).

### Query Performance

**Expected load:** Low. Queries happen during bootstrap (once per macro startup), not per-request.

**Indexes:**
- `service_macros.macro_name` (unique index) - O(log N) lookup
- `service_capabilities` routing key (unique index) - O(log N) lookup
- `macro_capabilities` foreign keys (indexed) - O(log N) join

**No full table scans** in critical paths.

---

## Known Patterns

### Builder Pattern (CapabilityDescriptor)

**Why builder?**
- 6 required fields (serviceName, capability, version, method, path, handlerClass)
- Validation at construction (fail-fast)
- Immutable result (thread-safe)

**Usage:**
```java
CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
    .serviceName("messages")
    .capability(Capability.READ)
    .capabilityVersion(1)
    .method("GET")
    .pathPattern("/content")
    .handlerClassName("com.example.GetContentHandler")
    .build();
```

### Record Types (Request/Response)

**Why records?**
- Immutable by default
- Compact constructor for validation
- Auto-generated equals/hashCode/toString
- Clear data transfer intent

**Usage:**
```java
public record RegistrationRequest(
    String macroName,
    String endpoint,
    List<CapabilityDescriptor> capabilities
) {
    public RegistrationRequest {
        if (macroName == null || macroName.isBlank()) {
            throw new IllegalArgumentException("macroName is required");
        }
        // ... more validation
    }
}
```

---

## Future Work

### 1. Implement CapabilityResolver

**Goal:** Query registry to resolve routing keys to endpoints.

**Implementation:**
```java
@Component
public class SpringCapabilityResolver implements CapabilityResolver {
    private final ServiceRegistryRepository repository;

    @Override
    public List<Endpoint> resolve(String routingKey) {
        // Query service_capabilities by routing key
        // Join to macro_capabilities
        // Join to service_macros
        // Return List<Endpoint> with logical names
    }
}
```

**Caching:** Use registry version to invalidate cache when capabilities change.

---

### 2. Health-Based Routing (If Needed)

**Goal:** Exclude unhealthy macros from resolution.

**Approach:**
- Add `health_status` column to `service_macros` (HEALTHY, UNHEALTHY)
- Periodically query macro health endpoints, update status
- Filter out UNHEALTHY macros in resolver query

**Note:** May not be needed if infrastructure (load balancers, service mesh) handles health checking.

---

### 3. Batch Registration

**Goal:** Register multiple macros in single API call.

**Use case:** Bulk import of capabilities from configuration file.

**Implementation:**
```java
public List<RegistrationResult> registerMacros(List<RegistrationRequest> requests) {
    return requests.stream()
        .map(this::registerMacro)
        .collect(Collectors.toList());
}
```

**Trade-off:** Single transaction (all-or-nothing) vs multiple transactions (partial success). Choose based on use case.

---

## Summary

This implementation provides a **simple, transactional registry** for capability-based routing:

- **Three tables** (macros, capabilities, junction table)
- **Idempotent operations** (ON CONFLICT clauses)
- **Spring JDBC** for explicit SQL control
- **TestContainers** for integration testing with real PostgreSQL
- **Cache invalidation** via registry version counter
- **Bridge Pattern** keeps Spring types out of core

**Key trade-offs:**
- Simplicity over features (removed deployment orchestration)
- Explicit SQL over ORM (Spring JDBC, not JPA)
- Database constraints over application logic (uniqueness enforced by DB)
