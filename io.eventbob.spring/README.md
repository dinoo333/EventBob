# EventBob Spring Implementation

**Spring Boot implementation of EventBob infrastructure** (Bridge Pattern)

## What Is This Module?

This is the **Spring-based implementation of EventBob** - the complete macro-service infrastructure using Spring Boot and Spring JDBC.

**Architecture Pattern:** Bridge
- **Abstraction:** `io.eventbob.core` (domain model, ports, interfaces)
- **Implementation:** `io.eventbob.spring` (this module - Spring-based concrete implementation)

**Not just a registry:** This module provides the complete EventBob infrastructure including capability scanning, registration, instance tracking, deployment management, and persistence.

## What This Implementation Provides
- **JAR introspection**: Scan JARs for `@EventHandlerCapability` annotations
- **Capability registration**: Store what operations each service provides
- **Instance tracking**: Know which physical instances are running
- **Blue/green deployments**: Progressive traffic shifting with rollback
- **Conflict detection**: Warn when capability versions mismatch

## Architecture

### Three-Table Model

```
┌────────────────────────┐
│ service_capabilities   │  What operations exist
│  - service_name        │
│  - capability (R/W/A)  │
│  - capability_version  │
│  - method, path        │
│  - deployment_state    │
└────────────────────────┘
            ↓
┌────────────────────────┐
│ macrolith_capabilities  │  Join: which macroliths provide which capabilities
└────────────────────────┘
            ↓
┌────────────────────────┐
│ service_macroliths      │  Which logical deployment units exist
│  - macrolith_name          │
│  - instance_id         │
│  - endpoint            │
│  - status, heartbeat   │
└────────────────────────┘
```

### Deployment States

- **BLUE**: New version being rolled out (0% → 100% traffic)
- **GREEN**: Stable production version (100% traffic)
- **GRAY**: Rolled back or superseded (0% traffic, available for emergency rollback)
- **RETIRED**: Decommissioned (JARs unloaded, instances terminated)

## Usage

### 1. Annotate Event Handlers

```java
@EventHandlerCapability(
  service = "messages",
  capability = "READ",
  capabilityVersion = 1,
  operations = {"GET /content", "GET /bulk-content"}
)
public class GetMessageContentHandler implements EventHandler {
  @Override
  public Event handle(Event event) {
    // Implementation
  }
}
```

### 2. Bootstrap Macrolith-Service

```java
MacroServiceBootstrap bootstrap = new MacroServiceBootstrap(scanner, registrar, repository);

BootstrapConfig config = new BootstrapConfig(
  "messages-readonly-macrolith",     // Macrolith name
  "pod-1",                        // Instance ID (unique per instance)
  List.of(Path.of("/app/messages-service.jar")),
  "1.2.3",                        // JAR version
  DeploymentState.BLUE,           // Initial state (blue = new rollout)
  null,                           // Deployment version (null = auto-increment)
  null,                           // Endpoint (null = auto-detect)
  8080                            // Port
);

BootstrapResult result = bootstrap.bootstrap(config);

System.out.println("Registered " + result.capabilities().size() + " capabilities");
```

### 3. Query Registry

```java
// Find all capabilities for a service
List<CapabilityRecord> capabilities = repository.findAllCapabilitiesByRoutingKey(
  "messages",
  Capability.READ,
  "GET",
  "/content"
);

// Detect conflicts
List<CapabilityConflict> conflicts = repository.detectConflicts();
```

## Concurrent Registration

Multiple instances of the same macrolith can register simultaneously. The system handles this via:

1. **Idempotent operations**: `INSERT ... ON CONFLICT DO NOTHING`
2. **Capability sharing**: Multiple instances link to the same capability record
3. **Instance isolation**: Each instance gets its own UUID

### Example: Scaling from 1 → 3 instances

```
T=0: pod-1 starts → registers capabilities (id=cap-123)
T=0: pod-2 starts → tries to register same capabilities
       → ON CONFLICT: uses existing cap-123
       → creates new instance record
       → links instance to cap-123
T=0: pod-3 starts → same as pod-2

Result:
  - 1 capability record (cap-123)
  - 3 instance records (pod-1, pod-2, pod-3)
  - 3 links in macrolith_capabilities
```

## Conflict Detection

The system detects when the same routing key has different capability versions:

```java
// v1 deployed
@EventHandlerCapability(
  service = "messages",
  capability = "READ",
  capabilityVersion = 1,
  operations = {"GET /content"}
)

// v2 deployed (same routing key, different version)
@EventHandlerCapability(
  service = "messages",
  capability = "READ",
  capabilityVersion = 2,  // Conflict!
  operations = {"GET /content"}
)
```

**Result**: Registration of v2 is skipped, warning emitted.

**Why?** This indicates a misconfiguration - two active deployments with incompatible capability contracts.

## Database Schema

See `src/main/resources/db/migration/V001__create_service_registry.sql`

Key tables:
- `service_capabilities`: Capability definitions
- `service_macroliths`: Physical instances
- `macrolith_capabilities`: Join table
- `deployment_history`: Audit log
- `registry_version`: Cache invalidation (bumped on every change)

## Testing

Integration tests use Testcontainers for real PostgreSQL:

```bash
mvn test
```

Tests cover:
- Capability registration
- Multiple instances with same capabilities
- Conflict detection
- Idempotent re-registration
- Registry version bumping

## Why "Spring" Not "Registry"?

This module is named `io.eventbob.spring` because:
1. It's the **Spring implementation** of EventBob (Bridge Pattern)
2. It provides ALL infrastructure capabilities, not just registry
3. It enables peer implementations like `io.eventbob.dropwizard` to coexist
4. The domain abstraction lives in `io.eventbob.core`

## Dependencies

- **Spring Boot**: JDBC, transactions, dependency injection
- **PostgreSQL**: Persistence layer
- **Flyway**: Schema migrations
- **ClassGraph**: JAR scanning for capabilities
- **Testcontainers**: Integration testing

## Alternative Implementations

The Bridge Pattern enables multiple framework implementations:
- `io.eventbob.spring` (this module) - Spring Boot + Spring JDBC
- `io.eventbob.dropwizard` (planned) - Dropwizard + JDBI
- `io.eventbob.micronaut` (future) - Micronaut + Micronaut Data

All implementations provide the same capabilities, just using different frameworks.

## Future Enhancements

- [ ] Health checking (mark instances unhealthy)
- [ ] Heartbeat monitoring (detect stale instances)
- [ ] Automatic cleanup (retire gray versions after grace period)
- [ ] Metrics (registration failures, conflicts, version distribution)
- [ ] In-memory cache (version-based invalidation)
