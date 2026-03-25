# EventBob Deployment Refactoring Guide

## Overview

EventBob's location transparency enables configuration-only reorganization of microliths. Handlers can be moved between microliths, microliths can be merged or split, and capabilities can be consolidated—all without changing handler code.

**Key insight:** The routing layer treats local (JAR-loaded) and remote (HTTP-wrapped) handlers identically. Moving a capability from local to remote is purely a configuration change.

## Configuration Mechanics

EventBob microliths are configured via Spring `@Bean` declarations in the application class, not via `application.yml` property binding. There are three bean types:

### Inline Handlers (`HandlerLifecycle`)

Declare one bean per inline handler. These execute in-process without JAR loading.

```java
@Bean
public EchoHandlerLifecycle echoHandlerLifecycle() {
    return new EchoHandlerLifecycle();
}
```

### JAR-based Handlers (`List<Path>` named `handlerJarPaths`)

Declare a `List<Path>` bean named exactly `handlerJarPaths`. Each path is loaded as an isolated handler JAR at startup.

```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/echo.jar"),
        Path.of("/opt/handlers/lower.jar")
    );
}
```

### Remote Capabilities (`List<RemoteCapability>`)

Declare a `List<RemoteCapability>` bean. Events targeting these capabilities are forwarded via HTTP to the configured microlith.

```java
@Bean
public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://microlith-b:8080"))
    );
}
```

## Refactoring Scenarios

### Scenario 1: Move JAR Between Microliths

**Use case:** Redistribute load, co-locate related capabilities, or optimize network topology.

**Example:** Move `echo.jar` from Microlith A to Microlith B.

#### Current State
```
Microlith A (port 8080)
├── echo.jar (local)
└── lower.jar (local)

Microlith B (port 8081)
└── upper.jar (local)
```

**Microlith A configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/echo.jar"),
        Path.of("/opt/handlers/lower.jar")
    );
}
```

**Microlith B configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(Path.of("/opt/handlers/upper.jar"));
}

@Bean
public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("echo",  URI.create("http://microlith-a:8080")),
        new RemoteCapability("lower", URI.create("http://microlith-a:8080"))
    );
}
```

#### Target State
```
Microlith A (port 8080)
└── lower.jar (local)

Microlith B (port 8081)
├── upper.jar (local)
└── echo.jar (local)
```

**Microlith A configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(Path.of("/opt/handlers/lower.jar"));
}

@Bean
public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("echo",  URI.create("http://microlith-b:8081")),
        new RemoteCapability("upper", URI.create("http://microlith-b:8081"))
    );
}
```

**Microlith B configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/upper.jar"),
        Path.of("/opt/handlers/echo.jar")
    );
}
```

#### Configuration Changes
- **Microlith A:** Remove `echo.jar` from the `handlerJarPaths` bean, add an `"echo"` entry to the `remoteCapabilities` bean pointing to B
- **Microlith B:** Add `echo.jar` to the `handlerJarPaths` bean, remove the `"echo"` entry from the `remoteCapabilities` bean

**Zero code changes to handlers.** Routing layer adapts automatically.

---

### Scenario 2: Merge Microliths

**Use case:** Consolidate underutilized services, simplify deployment topology, reduce operational overhead.

**Example:** Merge Microlith B into Microlith A.

#### Current State
```
Microlith A (port 8080)
├── echo.jar (local)
└── lower.jar (local)

Microlith B (port 8081)
└── upper.jar (local)
```

#### Target State
```
Microlith A (port 8080)
├── echo.jar (local)
├── lower.jar (local)
└── upper.jar (local)

Microlith B (decommissioned)
```

**Microlith A configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/echo.jar"),
        Path.of("/opt/handlers/lower.jar"),
        Path.of("/opt/handlers/upper.jar")
    );
}
// remoteCapabilities bean removed entirely
```

#### Configuration Changes
- **Microlith A:** Add `upper.jar` to the `handlerJarPaths` bean; remove the `remoteCapabilities` bean (or remove the `"upper"` entry from it if other remote capabilities remain)
- **Microlith B:** Decommission after cutover

**Result:** All capabilities now local in Microlith A. No inter-service HTTP calls for these capabilities.

---

### Scenario 3: Extract to New Microlith

**Use case:** Scale out a hot capability, isolate resource-intensive handlers, prepare for team ownership boundaries.

**Example:** Extract `upper.jar` to a new dedicated Microlith C.

#### Current State
```
Microlith A (port 8080)
├── echo.jar (local)
├── lower.jar (local)
└── upper.jar (local)
```

#### Target State
```
Microlith A (port 8080)
├── echo.jar (local)
└── lower.jar (local)

Microlith C (port 8082)
└── upper.jar (local)
```

**Microlith A configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/echo.jar"),
        Path.of("/opt/handlers/lower.jar")
    );
}

@Bean
public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://microlith-c:8082"))
    );
}
```

**Microlith C configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(Path.of("/opt/handlers/upper.jar"));
}
```

#### Configuration Changes
- **Microlith A:** Remove `upper.jar` from the `handlerJarPaths` bean; add a `"upper"` entry to the `remoteCapabilities` bean pointing to C
- **Microlith C:** New microlith with a `handlerJarPaths` bean containing `upper.jar`

**Use case:** Microlith C can now scale independently. If `upper` handles CPU-intensive transformations, it can run on larger instances without affecting `echo` and `lower`.

---

### Scenario 4: Consolidate Related Capabilities

**Use case:** Co-locate capabilities that frequently communicate, optimize chatty interactions, reduce latency.

**Example:** Move `lower.jar` and `upper.jar` to same microlith (they're often called together by `echo`).

#### Current State
```
Microlith A (port 8080)
└── echo.jar (local)

Microlith B (port 8081)
└── lower.jar (local)

Microlith C (port 8082)
└── upper.jar (local)
```

**Issue:** `echo` handler calls both `lower` and `upper`. This results in 2 HTTP round-trips per echo request.

#### Target State
```
Microlith A (port 8080)
└── echo.jar (local)

Microlith B (port 8081)
├── lower.jar (local)
└── upper.jar (local)

Microlith C (decommissioned)
```

**Microlith A configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(Path.of("/opt/handlers/echo.jar"));
}

@Bean
public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("lower", URI.create("http://microlith-b:8081")),
        new RemoteCapability("upper", URI.create("http://microlith-b:8081"))
    );
}
```

**Microlith B configuration:**
```java
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Path.of("/opt/handlers/lower.jar"),
        Path.of("/opt/handlers/upper.jar")
    );
}
```

#### Configuration Changes
- **Microlith B:** Add `upper.jar` to the `handlerJarPaths` bean
- **Microlith A:** Update the `"upper"` entry in the `remoteCapabilities` bean to point to B instead of C
- **Microlith C:** Decommission

**Result:** When `echo` calls `lower` and `upper`, both are now handled in Microlith B. If they share data or state, they can do so in-process.

---

## Zero-Downtime Deployment Strategy

### Core Principle

**Always deploy the destination microlith first (adds capability), then the source microlith (removes capability).**

This ensures that at every moment during deployment, at least one microlith can handle the capability.

### Why This Works

EventBob's location transparency allows **both microliths to have the same capability temporarily** during transition:
- Both can handle requests independently
- No conflicts because handlers don't share state
- After transition completes, source forwards to destination

### Production Deployment: Rolling Restarts

For load-balanced microliths with multiple instances:

#### Phase 1: Add Capability to Destination

**Example:** Moving `echo.jar` from Microlith A (3 instances: A1, A2, A3) to Microlith B (3 instances: B1, B2, B3).

```
1. Deploy Microlith B with echo.jar added to handlerJarPaths
2. Rolling restart: B1 → B2 → B3

State during rollout:
├── Microlith A (all instances): Handle echo locally ✓
├── Microlith B (updated instances): Can handle echo locally ✓
└── Microlith B (pending instances): No echo yet (doesn't matter—A is authoritative)

Risk: None. A handles all production traffic.
```

#### Health Check Gate

**Before proceeding to Phase 2:**

```bash
# Send test requests to verify B can handle echo
curl -X POST http://microlith-b:8081/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "test",
    "target": "echo",
    "payload": "health check"
  }'

# Expected: Response with source="echo", no errors
# Repeat for all B instances
```

**Criteria to proceed:**
- All B instances respond successfully
- Response times within acceptable threshold
- No errors in B logs related to echo handler

#### Phase 2: Remove Capability from Source

```
1. Deploy Microlith A with echo moved to remoteCapabilities
2. Rolling restart: A1 → A2 → A3

State during rollout:
├── Microlith A (updated instances): Forward echo to B ✓
├── Microlith A (pending instances): Handle echo locally ✓
└── Microlith B (all instances): Handle echo locally ✓

Result: Seamless transition. All requests handled throughout rollout.
```

#### Complete Cutover

```
After A3 restarts:
├── All A instances: Forward echo to B
└── All B instances: Handle echo locally

Final state: Migration complete, zero downtime achieved.
```

---

## Blue-Green Deployment Alternative

For environments with blue-green infrastructure:

### Procedure

```
1. Blue environment (current):
   ├── Microlith A: has echo.jar locally
   └── Microlith B: treats echo as remote → A

2. Green environment (new):
   ├── Microlith A: treats echo as remote → B
   └── Microlith B: has echo.jar locally

3. Deploy green environment completely
4. Verify health checks on green
5. Switch load balancer: blue → green atomically
6. Zero downtime: green is fully ready before cutover
```

### Advantages
- Atomic cutover reduces transition window
- Easy rollback (switch back to blue)
- Full environment testing before production traffic

### Disadvantages
- Requires 2x infrastructure during transition
- More complex orchestration
- Overkill for simple JAR moves

**Recommendation:** Use rolling restarts for routine refactoring. Reserve blue-green for major topology changes or high-risk migrations.

---

## Production Considerations

### Health Checks

EventBob provides a built-in `healthcheck` capability registered unconditionally on every microlith. Load balancers and orchestrators can verify microlith availability by routing an event to the `healthcheck` target and checking for a successful response.

### Monitoring

**Key metrics during migration:**
- Request latency (local vs remote)
- Error rates by capability
- HTTP connection pool saturation
- Handler execution times

**Alerting thresholds:**
- Error rate spike >2x baseline
- p99 latency >3x baseline
- Connection timeouts to destination

### Rollback Plan

If issues arise during Phase 2 (removing capability from source):

```
1. Stop rolling restart of source microlith
2. Revert to previous configuration (capability local)
3. Rolling restart back to stable state
4. Investigate root cause before retry
```

**Key insight:** Phase 1 (adding to destination) is reversible. Phase 2 (removing from source) should only proceed after health checks pass.

---

## Anti-Patterns

### ❌ Anti-Pattern 1: Deploy Source First

```
1. Deploy Microlith A (removes echo, forwards to B)
2. Deploy Microlith B (adds echo)

Problem: Between deployments, A forwards to B, but B doesn't have echo yet.
Result: DOWNTIME—all echo requests fail.
```

**Correct approach:** Deploy B first (adds echo), then A (forwards to B).

---

### ❌ Anti-Pattern 2: Skip Health Checks

```
1. Deploy destination with capability
2. Immediately deploy source (remove capability)

Problem: If destination deployment failed silently (JAR didn't load, classpath issue),
source will forward to broken destination.
Result: DOWNTIME—all requests fail.
```

**Correct approach:** Verify destination health before proceeding to remove from source.

---

### ❌ Anti-Pattern 3: Big Bang Migration

```
Move 10 JARs across 5 microliths in a single deployment.

Problem: Too many moving parts. Hard to isolate issues. Rollback complexity high.
Result: High risk, difficult troubleshooting.
```

**Correct approach:** Migrate one JAR at a time. Verify each migration before proceeding to next.

---

### ❌ Anti-Pattern 4: Ignoring Handler Dependencies

```
Move echo.jar to Microlith B, but leave lower.jar and upper.jar in Microlith A.

Problem: Echo handler calls lower and upper. If co-located before, now introduces HTTP overhead.
Result: Latency increase, chatty network calls.
```

**Correct approach:** Map handler dependencies first. Co-locate frequently communicating handlers.

---

## Summary

| Refactoring Scenario | Configuration Change | Code Change | Deployment Order |
|---------------------|----------------------|-------------|------------------|
| Move JAR | Update `handlerJarPaths` and `remoteCapabilities` | None | Destination first |
| Merge microliths | Add JARs to target, decommission source | None | Target first |
| Extract to new | Remove JAR from source, create new microlith | None | New first |
| Consolidate capabilities | Move JARs to same microlith | None | Destination first |

**Key principles:**
1. Location transparency enables configuration-only refactoring
2. Deploy destination first, source second (zero downtime)
3. Health checks between phases prevent cascading failures
4. Rolling restarts minimize blast radius
5. One migration at a time reduces risk

EventBob's architecture makes microlith reorganization a low-risk operational task rather than a risky code refactoring.
