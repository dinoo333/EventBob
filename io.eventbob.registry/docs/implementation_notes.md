# Service Registry Implementation Notes

**Module:** io.eventbob.registry
**Last Updated:** 2026-02-12

## Data Structures

### Three-Table Model

**Design rationale:** Separate tables for capabilities, instances, and their many-to-many relationship.

```sql
service_capabilities       -- What operations exist (independent of instances)
instance_capabilities      -- Join table (which instances provide which capabilities)
service_instances          -- Which physical instances are running
```

**Why not a single table?**
- Capabilities can exist before instances register (pre-declared capabilities)
- Instances can provide multiple capabilities (composition)
- Capabilities can be provided by multiple instances (replication)

**Why not denormalize?**
- Denormalized: `{instance_id, capability_1, capability_2, ...}` creates special cases (NULL capability columns, dynamic schema)
- Normalized: `instance_capabilities` join table makes multiple instances sharing capabilities natural, not an edge case

### Idempotency via ON CONFLICT

**Pattern:** All inserts use `ON CONFLICT (...) DO UPDATE SET ...` for idempotent registration.

**Example (instance registration):**
```sql
INSERT INTO service_instances (macro_name, instance_id, endpoint, status, heartbeat_at, deployment_version)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (macro_name, instance_id)
DO UPDATE SET
    endpoint = EXCLUDED.endpoint,
    status = EXCLUDED.status,
    heartbeat_at = EXCLUDED.heartbeat_at;
```

**Rationale:**
- Macro-services restart and re-register (Kubernetes pod restarts, rolling updates)
- Re-registration should update heartbeat, not create duplicates
- Idempotency makes retry logic simple (just re-register)

**Trade-off:** `ON CONFLICT` is PostgreSQL-specific. Portability to MySQL requires `ON DUPLICATE KEY UPDATE`. Portability to H2 requires `MERGE`.

### Conflict Detection Query

**Pattern:** LEFT JOIN to detect version mismatches between declared and registered capabilities.

**SQL:**
```sql
SELECT sc.routing_key, sc.capability_version as registered_version, ? as declared_version
FROM service_capabilities sc
WHERE sc.routing_key = ?
  AND sc.deployment_state = 'GREEN'
  AND sc.capability_version != ?
```

**Interpretation:**
- If query returns rows, a conflict exists (declared version != GREEN version)
- Empty result means no conflict (either no GREEN exists, or versions match)

**Rationale:** Operators need to know when new deployments have capability version drift before promoting to BLUE.

### Unique Indexes for Business Rules

**Single GREEN rule:**
```sql
CREATE UNIQUE INDEX idx_single_green_capability
ON service_capabilities (routing_key)
WHERE deployment_state = 'GREEN';
```

**Single BLUE rule:**
```sql
CREATE UNIQUE INDEX idx_single_blue_capability
ON service_capabilities (routing_key)
WHERE deployment_state = 'BLUE';
```

**Why partial indexes?**
- Multiple GRAY versions are allowed (historical record)
- Multiple RETIRED versions are allowed (pending cleanup)
- Only GREEN and BLUE are constrained (business invariants)

**Database-level enforcement:** Violating these rules causes `unique constraint violation` exception, not silent data corruption.

## Patterns in Use

### Builder Pattern

**Where:** `CapabilityDescriptor`

**Justification:**
- 6 required fields with validation
- Immutable once built
- Clear construction API

**Implementation:**
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

**Alternative considered:** Telescoping constructor - too many parameters, unclear order.

### Repository Pattern

**Where:** `ServiceRegistryRepository`

**Justification:**
- Complex SQL operations (idempotent upserts, joins, conflict detection)
- Transaction boundary (multiple tables updated atomically)
- Isolates JDBC from service layer

**Implementation:** Spring `@Repository` + `JdbcTemplate`

**Why not JPA?** Complex `ON CONFLICT` clauses are idiomatic in SQL but awkward in JPA.

### Service Pattern

**Where:** `CapabilityRegistrar`

**Justification:**
- Orchestrates multiple repository calls
- Implements domain logic (conflict detection, deployment versioning)
- Transaction boundary (`@Transactional`)

**Implementation:** Spring `@Service`

**Layering:** Service calls Repository, not reverse.

### Record for DTOs

**Where:** `ServiceRegistryRepository.CapabilityRecord`, `CapabilityConflict`

**Justification:**
- Immutable data transfer from SQL row to Java object
- Structural equality (two records with same fields are equal)
- Compact syntax (no boilerplate getters/setters/equals/hashCode)

**Pattern:**
```java
record CapabilityRecord(
    String routingKey,
    String serviceName,
    Capability capability,
    int capabilityVersion,
    String method,
    String pathPattern,
    DeploymentState deploymentState
) {
    // Compact, immutable, structural equality
}
```

**Alternative considered:** `@Data` Lombok class - records are built-in and more explicit.

### Enum for Closed Value Sets

**Where:** `DeploymentState`, `InstanceStatus`, `Capability` (imported from core)

**Justification:**
- Fixed set of values (BLUE/GREEN/GRAY/RETIRED, HEALTHY/UNHEALTHY/DRAINING/TERMINATED)
- Type safety (can't pass invalid string)
- Database enum types match Java enum values

**Pattern:**
```sql
CREATE TYPE deployment_state AS ENUM ('BLUE', 'GREEN', 'GRAY', 'RETIRED');
```
```java
public enum DeploymentState { BLUE, GREEN, GRAY, RETIRED }
```

**Trade-off:** Adding enum values requires database migration. Strings would be more flexible but lose type safety.

## Spring Boot Integration

### Dependency Injection via Constructor

**Pattern:** All `@Service` and `@Repository` classes use constructor injection.

**Example:**
```java
@Service
public class CapabilityRegistrar {
    private final ServiceRegistryRepository repository;

    public CapabilityRegistrar(ServiceRegistryRepository repository) {
        this.repository = repository;
    }
}
```

**Why not field injection (`@Autowired` on field)?**
- Constructor injection makes dependencies explicit
- Testable without Spring context (pass mock in constructor)
- Immutable fields (can be `final`)

### JdbcTemplate Usage

**Pattern:** All SQL executed via Spring `JdbcTemplate`.

**Row mapping:**
```java
CapabilityRecord record = jdbc.queryForObject(sql, (rs, rowNum) ->
    new CapabilityRecord(
        rs.getString("routing_key"),
        rs.getString("service_name"),
        Capability.valueOf(rs.getString("capability")),
        rs.getInt("capability_version"),
        rs.getString("method"),
        rs.getString("path_pattern"),
        DeploymentState.valueOf(rs.getString("deployment_state"))
    ),
    routingKey
);
```

**Enum mapping:** `Capability.valueOf(rs.getString("capability"))` - requires DB enum names match Java enum names exactly.

**NULL handling:** Use `rs.getObject(...)` for nullable columns, `rs.getString(...)` for NOT NULL columns.

### Transaction Management

**Pattern:** `@Transactional` on service methods that modify multiple tables.

**Example:**
```java
@Transactional
public RegistrationResult registerInstance(RegistrationRequest request) {
    repository.registerInstance(...);
    for (CapabilityDescriptor capability : request.capabilities()) {
        repository.registerCapability(...);
        repository.linkInstanceCapability(...);
    }
    repository.recordDeploymentEvent(...);
    repository.incrementRegistryVersion();
    return new RegistrationResult(...);
}
```

**Rollback:** If any operation throws exception, entire transaction rolls back (instance + capabilities + linkage + audit all-or-nothing).

**Isolation level:** Defaults to `READ_COMMITTED` (sufficient for registry, prevents dirty reads).

## Test Strategy

### Integration Tests with Testcontainers

**Pattern:** Real PostgreSQL via Testcontainers, Spring Boot test context.

**Example:**
```java
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CapabilityRegistrationIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        // ...
    }
}
```

**Rationale:**
- Tests run against real PostgreSQL (not H2 pretending to be PostgreSQL)
- Schema compatibility verified (enums, partial indexes work)
- Flyway migrations run before tests

**Trade-off:** Slower than H2, requires Docker, but eliminates "works in tests, fails in production."

**Current status:** Integration tests are `@Disabled("Docker not available in current environment")`. Must run in CI.

### Architecture Tests with ArchUnit

**Pattern:** `RegistryArchitectureTest` enforces dependency rules.

**Tests:**
1. `registryCanDependOnCore()` - verifies only allowed imports
2. `registryShouldNotDependOnOtherModules()` - prevents cycles
3. `noCyclicDependencies()` - enforces DAG (currently flat)
4. `generateMermaidDependencyGraph()` - auto-generates docs/registry_dependency_graph.md

**Execution:** Tests run on every build (not just CI). Violations fail the build.

**Martin Metrics:** Generated dependency graph shows Abstractedness, Instability, Distance for package health tracking.

### Coverage Gaps (Known Debt)

**Missing unit tests:**
1. **`CapabilityScanner`** - Complex annotation parsing, multiple operations handling, error cases (malformed annotation, invalid capability, missing fields)
2. **`MacroServiceBootstrap`** - Orchestration logic, failure modes (no capabilities found, scanner throws, network detection fails)

**Rationale for gap:**
- Scanner requires ClassGraph mocking or test JARs (complex setup)
- Bootstrap requires Spring context or mocking registrar + scanner

**Plan:** Add unit tests in next iteration after proving integration tests cover happy path.

**Current coverage:**
- ✅ Integration test: happy path registration
- ✅ Integration test: multiple instances sharing capabilities
- ✅ Integration test: conflict detection
- ✅ Integration test: idempotent re-registration
- ✅ Architecture tests: dependency rules
- ❌ Unit test: scanner parsing logic
- ❌ Unit test: bootstrap orchestration
- ❌ Unit test: repository edge cases (NULL values, empty results)

## Module Conventions

### Naming

**Classes encode role:**
- `*Descriptor` - value object (CapabilityDescriptor)
- `*Registrar` - service (CapabilityRegistrar)
- `*Repository` - data access (ServiceRegistryRepository)
- `*Scanner` - logic/algorithm (CapabilityScanner)
- `*Bootstrap` - orchestration (MacroServiceBootstrap)

**Methods are verbs:** `registerInstance()`, `scanJar()`, `detectConflicts()`

**Constants:** `UPPERCASE_SNAKE_CASE` for static finals

### Annotations

**Spring stereotypes:**
- `@Service` - service layer (business logic, orchestration)
- `@Repository` - data access layer (JDBC)
- `@Component` - utility classes (if needed)

**Transaction management:**
- `@Transactional` - on service methods that modify data
- Read-only queries do not need `@Transactional` (no write locks)

**Test annotations:**
- `@SpringBootTest` - integration tests (full Spring context)
- `@Testcontainers` - Docker containers for tests
- `@Disabled` - tests that require environment setup

### Logging

**Pattern:** SLF4J via `LoggerFactory.getLogger(ClassName.class)`

**Levels:**
- `DEBUG` - detailed flow (JAR scanning progress, SQL execution)
- `INFO` - significant events (instance registered, conflicts detected)
- `WARN` - recoverable errors (capability skipped, version mismatch)
- `ERROR` - unrecoverable errors (database connection failed)

**Structured logging:** Use placeholders, not concatenation:
```java
log.info("Registered instance {} with {} capabilities", instanceId, count);  // Good
log.info("Registered instance " + instanceId + " with " + count);  // Bad
```

## Known Issues

### 1. Unused Schema Columns

**Columns defined but not used:**
- `rollout_policy` JSONB (future: canary percentages, rollback rules)
- `became_blue_at`, `became_green_at`, `became_gray_at`, `retired_at` (future: state transition timestamps)

**Impact:** Wasted storage (minimal), schema drift (moderate).

**Plan:** Either implement rollout logic (use columns) or remove columns in V002 migration.

**Current decision:** Keep columns (forward-looking schema, avoids migration complexity).

### 2. Multiple Operations Handling (FIXED)

**Previous issue:** `CapabilityScanner` silently dropped all but first operation when handler declared multiple.

**Fix:** Scanner now creates one descriptor per operation and returns a list. Registrar loops over all descriptors.

**Status:** Resolved in this commit.

### 3. TODO Comments in Code

**`CapabilityRegistrar` line 150:**
```java
// TODO: Emit metric for conflict detection
```

**Impact:** Observability gap (can't track conflict rate in production).

**Plan:** Emit metric in future observability pass (Micrometer integration).

### 4. No Scanner Interface

**Current state:** `CapabilityScanner` is concrete class, hardcodes `URLClassLoader`.

**Impact:** Hard to test (requires real JARs), hard to mock.

**Plan:** Extract interface when testing pain increases or when alternative scanner needed (e.g., build-time validation).

### 5. Integration Tests Disabled

**Current state:** `CapabilityRegistrationIntegrationTest` is `@Disabled("Docker not available in current environment")`.

**Impact:** Schema/code alignment not verified locally.

**Plan:** CI must run these tests. Local developers can skip if Docker unavailable.

## Module Dependencies

**External libraries:**
- Spring Boot 3.2.2 (JDBC, transactions, DI)
- PostgreSQL driver (JDBC connectivity)
- Flyway 10.x (migrations)
- ClassGraph 4.8.165 (JAR scanning)
- SLF4J 2.0.11 (logging abstraction)
- Testcontainers 1.19.3 (integration tests)
- ArchUnit 1.2.1 (architecture tests)

**Internal dependencies:**
- io.eventbob.core (domain model)

**Zero dependencies on:**
- HTTP frameworks (no Jersey, no Spring Web)
- Serialization frameworks (no Jackson, no Gson)
- Other EventBob modules (no io.eventbob.api, etc.)

## Performance Considerations

### Database Queries

**Indexed columns:**
- `service_capabilities(routing_key)` - fast lookups by capability operation
- `service_instances(macro_name, instance_id)` - fast instance registration
- `instance_capabilities(instance_id)`, `instance_capabilities(routing_key)` - fast join queries

**Query patterns:**
- Instance registration: Single `INSERT ... ON CONFLICT` (O(1))
- Capability registration: Single `INSERT ... ON CONFLICT` per capability (O(n) where n = capabilities)
- Conflict detection: Single `SELECT` with `WHERE deployment_state = 'GREEN'` (indexed, O(1))
- Endpoint resolution (future): Single `JOIN` query (O(k) where k = instances providing capability)

**N+1 query risk:** Avoided. `registerInstance()` loops over capabilities but each is a single parameterized query (not a separate SELECT per capability).

### Memory

**In-memory structures:**
- `List<CapabilityDescriptor>` - ephemeral, discarded after registration
- `RegistrationResult` - small DTO (instance ID + conflict list)
- No caching (registry is write-heavy, cache would be stale)

**ClassGraph memory:** JAR scanning uses off-heap memory (ClassGraph doesn't load classes into JVM heap).

### Concurrency

**Thread safety:**
- All beans are singletons (Spring default)
- Repository methods are stateless (no mutable fields)
- Transactions serialize conflicting writes (PostgreSQL row locks)

**Concurrent registration:**
- Multiple instances registering simultaneously are serialized by `ON CONFLICT` locks
- Safe: PostgreSQL handles race conditions (unique index violations)

## Summary

**Data model:** Three tables with join (capabilities, instances, linkage) + idempotency via `ON CONFLICT`

**Patterns:** Builder, Repository, Service, Record, Enum

**Spring Boot:** Constructor DI, JdbcTemplate, `@Transactional`

**Tests:** Integration (Testcontainers + PostgreSQL), Architecture (ArchUnit), Coverage gaps (scanner, bootstrap)

**Conventions:** Stereotype annotations, SLF4J logging, explicit SQL

**Known issues:** Unused schema columns, disabled integration tests locally, missing scanner tests, TODO comments

**Dependencies:** Spring Boot, PostgreSQL, Flyway, ClassGraph, core domain

**Next steps:** Add scanner unit tests, emit conflict metrics, enable integration tests in CI
