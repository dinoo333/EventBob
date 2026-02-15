# EventBob Implementation Notes

## What This File Contains

Implementation details for the EventBob registry system (io.eventbob.spring). For domain understanding, see `domain_spec.md`. For architecture, see `architecture.md`.

---

## Registry Data Model

### Three-Table Structure

```sql
service_capabilities   -- What operations exist (independent of macroliths)
macrolith_capabilities     -- Join table (which macroliths provide which capabilities)
service_macroliths         -- Which logical deployment units exist
```

**Why three tables?**
- Capabilities can exist before macroliths register (pre-declared capabilities)
- Macroliths can provide multiple capabilities (composition)
- Capabilities can be provided by multiple macroliths (replication)

### Idempotency via ON CONFLICT

Inserts use `ON CONFLICT` patterns for idempotent registration. Different tables use different strategies:

**Macroliths (last-wins):**
```sql
INSERT INTO service_macroliths (macrolith_name, endpoint)
VALUES (?, ?)
ON CONFLICT (macrolith_name)
DO UPDATE SET endpoint = EXCLUDED.endpoint;
```

**Capabilities (first-wins):**
```sql
INSERT INTO service_capabilities (...)
VALUES (...)
ON CONFLICT DO NOTHING;
-- Query for existing UUID if needed
```

**Rationale:**
- Macroliths use `DO UPDATE` (last registration wins) - restarting macroliths update their endpoint
- Capabilities use `DO NOTHING` (first registration wins) - capability definitions are immutable once registered

**Trade-off:** PostgreSQL-specific. Other databases require different syntax.

### Capability Uniqueness Constraint

Each routing key must be unique:

```sql
CONSTRAINT uq_capability_routing_key
  UNIQUE(service_name, capability, capability_version, method, path_pattern)
```

**Rationale:** Routing keys must be unambiguous. Two capabilities cannot have identical routing signatures.

**Trade-off:** Cannot have multiple active versions of same capability. If v1 and v2 exist, they must have different routing keys.

---

## JAR Scanning

### ClassGraph Library

**Why ClassGraph?**
- Fast (indexes JARs without loading classes unnecessarily)
- Annotation filtering (finds classes with `@EventHandlerCapability`)
- Works with nested JARs and modular JARs

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

**Performance:** Scanning a 50MB JAR takes ~200ms. Done once at bootstrap.

### Annotation Parsing

**Format:** `@EventHandlerCapability(operations = {"GET /users", "POST /users/{id}"})`

**Parsing logic:**
1. Split operation string on first space: `"GET /users"` → `["GET", "/users"]`
2. Validate method (must be HTTP verb)
3. Validate path (must start with `/`)
4. Create one `CapabilityDescriptor` per operation

**Error handling:** Invalid operations are logged and skipped. Scanning continues.

---

## Transaction Boundaries

### Bootstrap Transaction

Entire registration (macrolith + capabilities + links) is atomic:

```java
@Transactional
public RegistrationResult registerMacro(RegistrationRequest request) {
    UUID macrolithId = repository.registerMacro(request.macrolithName(), request.endpoint());

    for (CapabilityDescriptor cap : request.capabilities()) {
        UUID capId = repository.registerCapability(cap);
        repository.linkMacroCapability(macrolithId, capId);
    }

    return new RegistrationResult(macrolithId, capabilityIds);
}
```

**Why atomic?** Partial registration creates inconsistent state. Rollback ensures all-or-nothing semantics.

---

## Cache Invalidation

### Registry Version Counter

Trigger increments version on every table mutation:

```sql
CREATE TRIGGER trigger_capabilities_version
  AFTER INSERT OR UPDATE OR DELETE ON service_capabilities
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
- Works across multiple tables

**Trade-off:** Version bumps on every mutation. Fine-grained invalidation would require tracking which capabilities changed.

---

## Spring JDBC vs JPA

### Why Spring JDBC?

1. **Explicit SQL** - Full control, no hidden SELECT N+1 problems
2. **PostgreSQL features** - Can use `gen_random_uuid()`, triggers, partial unique indexes
3. **Performance** - No ORM overhead
4. **Simplicity** - No entity lifecycle management

**Trade-offs:**
- More boilerplate (manual result mapping)
- No lazy loading (but we don't need it—simple data model)

### Result Mapping

Lambda-based row mapper for each query:

```java
UUID macrolithId = jdbc.queryForObject(
    "SELECT id FROM service_macroliths WHERE macrolith_name = ?",
    (rs, rowNum) -> (UUID) rs.getObject("id"),
    macrolithName
);
```

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
- Real PostgreSQL (not H2 in-memory mock)
- Migrations run against real schema
- Tests verify actual SQL behavior

**Test cleanup:**
```java
@BeforeEach
void cleanDatabase() {
    jdbc.execute("TRUNCATE service_capabilities, service_macroliths, macrolith_capabilities CASCADE");
    jdbc.execute("UPDATE registry_version SET version = 1");
}
```

**Why TRUNCATE?** Faster than DELETE, resets sequences, honors CASCADE.

---

## Error Handling Strategy

### Fail-Fast During Bootstrap

EventBob follows a **fail-fast philosophy during startup**. If bootstrap cannot complete successfully, the macrolith will not start.

**Rationale:** A partially-initialized macrolith with missing capabilities creates silent routing failures at runtime. Better to fail loudly at startup than silently at request time.

### JAR Scanning Failures

**Failure scenario:** JAR file is missing, corrupted, or ClassGraph fails to scan.

**Behavior:** Bootstrap throws exception, macrolith startup fails.

**Implementation pattern:**
```java
// CapabilityScanner.scanJar() declares: throws Exception
try (ScanResult scanResult = new ClassGraph()
    .overrideClassLoaders(jarClassLoader)
    .enableAnnotationInfo()
    .enableClassInfo()
    .scan()) {
    // Parse capabilities
    // If parsing fails, exception propagates via method signature
}
```

Exceptions propagate to `MacroServiceBootstrap.bootstrap()`, which wraps them in `BootstrapException` and fails startup.

**Rationale:** If JAR scanning fails, we don't know what capabilities the macrolith should provide. Cannot start safely.

### Database Connection Failures

**Failure scenario:** PostgreSQL is unreachable, connection pool exhausted, or authentication fails.

**Behavior:** Spring Boot fails to start (data source initialization failure). Container/pod restarts per orchestrator policy.

**Rationale:** Without database, cannot register capabilities. Macrolith is unusable.

### Capability Conflicts

**Failure scenario:** Two handlers within the same JAR try to register identical routing keys.

**Behavior:** Database rejects insert via `CONSTRAINT uq_capability_routing_key`. Bootstrap transaction rolls back, macrolith fails to start.

**Example:**
```sql
-- Attempt to register duplicate routing key
INSERT INTO service_capabilities (service_name, capability, capability_version, method, path_pattern, handler_class)
VALUES ('messages', 'READ', 1, 'GET', '/content', 'Handler1');

-- Second handler tries same routing key
INSERT INTO service_capabilities (service_name, capability, capability_version, method, path_pattern, handler_class)
VALUES ('messages', 'READ', 1, 'GET', '/content', 'Handler2');
-- FAILS: violates uq_capability_routing_key
```

**Rationale:** Routing ambiguity is a design error. Must be fixed before deployment, not silently ignored.

### Idempotent Re-Registration

**Scenario:** Macrolith restarts (pod restart, rolling update). Same capabilities already registered.

**Behavior:** Existing capabilities are matched by routing key, existing IDs returned. No error, no duplication.

**Example:**
```java
UUID existingId = jdbc.query(
    "SELECT id FROM service_capabilities WHERE service_name = ? AND ...",
    ...).stream().findFirst().orElse(null);

if (existingId != null) {
    log.debug("Capability already registered, reusing id={}", existingId);
    return existingId; // Idempotent
}
```

**Rationale:** Multiple instances of the same macrolith (HA deployment) register safely. First wins, subsequent registrations are no-ops.

---

## Bridge Pattern Implementation

### Anti-Corruption Layer

Spring types stay in infrastructure layer, core types stay in core.

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

**Violation (NOT allowed):**
```java
// WRONG: Core importing from infrastructure
package io.eventbob.core;

import org.springframework.jdbc.core.JdbcTemplate;  // BOUNDARY VIOLATION
```

**Enforcement:** ArchUnit tests verify no core → infrastructure dependencies.

---

## Design Philosophy

EventBob registry follows a **simple, focused design:**

**What registry DOES track:**
- Which macroliths exist (logical deployment units)
- Which capabilities each macrolith provides (operations)
- How to route to macroliths (logical endpoint names)

**What registry does NOT track:**
- Physical instances or IPs (infrastructure's job via DNS/service mesh)
- Deployment states or versions (infrastructure concern)
- Health status or heartbeats (infrastructure concern)
- Deployment history or audit logs (separate concern)

**Rationale:** Registry is a **capability index**, not a deployment orchestrator. Keep it simple.

---

## Performance Characteristics

### Bootstrap Performance

Registering 50 capabilities takes ~150ms (local PostgreSQL):
- JAR scanning: ~100ms (ClassGraph)
- Registration: ~50ms (INSERT operations in single transaction)

**Bottleneck:** JAR scanning (file I/O).

### Query Performance

**Expected load:** Low. Queries happen during bootstrap (once per macrolith startup), not per-request.

**Indexes:**
- `service_macroliths.macrolith_name` (unique index) - O(log N)
- `service_capabilities` routing key (unique index) - O(log N)
- `macrolith_capabilities` foreign keys (indexed) - O(log N)

No full table scans in critical paths.

---

## Known Patterns

### Builder Pattern (CapabilityDescriptor)

**Why builder?**
- 6 required fields
- Validation at construction (fail-fast)
- Immutable result (thread-safe)

### Record Types (Request/Response)

**Why records?**
- Immutable by default
- Compact constructor for validation
- Auto-generated equals/hashCode/toString

---

## Open Questions About Implementation

1. **CapabilityResolver implementation:** Port interface exists but no adapter implemented yet. How should it query the registry? Cache strategy?
2. **Transport layer:** How do external clients invoke capabilities? HTTP adapter? gRPC? Message queue listener?
3. **JAR isolation:** Should each JAR get its own isolated classloader, or share a parent? Trade-offs?
