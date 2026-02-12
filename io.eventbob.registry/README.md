# EventBob Registry

Service registry for capability-based routing in EventBob macro-services.

## Overview

The registry enables:
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
│ instance_capabilities  │  Join: which instances provide which capabilities
└────────────────────────┘
            ↓
┌────────────────────────┐
│ service_instances      │  Which physical instances are running
│  - macro_name          │
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

### 2. Bootstrap Macro-Service

```java
MacroServiceBootstrap bootstrap = new MacroServiceBootstrap(scanner, registrar, repository);

BootstrapConfig config = new BootstrapConfig(
  "messages-readonly-macro",     // Macro name
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

Multiple instances of the same macro can register simultaneously. The system handles this via:

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
  - 3 links in instance_capabilities
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
- `service_instances`: Physical instances
- `instance_capabilities`: Join table
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

## Dependencies

- **Spring Boot**: JDBC, transactions
- **PostgreSQL**: Registry database
- **Flyway**: Schema migrations
- **ClassGraph**: JAR scanning
- **Testcontainers**: Integration tests

## Future Enhancements

- [ ] Health checking (mark instances unhealthy)
- [ ] Heartbeat monitoring (detect stale instances)
- [ ] Automatic cleanup (retire gray versions after grace period)
- [ ] Metrics (registration failures, conflicts, version distribution)
- [ ] Registry cache (in-memory, version-based invalidation)
