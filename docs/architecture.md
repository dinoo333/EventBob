# EventBob Architecture

## System Purpose

EventBob bundles multiple microservices into a macrolith to solve the chatty-application anti-pattern. When too many microservices cause network saturation and poor performance, EventBob loads their JARs into a single server for in-process communication.

---

## Bridge Pattern: Core + Implementations

EventBob separates abstraction from implementation:

```
┌─────────────────────────────────┐
│   io.eventbob.core              │  Abstraction (domain model + ports)
│   - Domain concepts             │
│   - Port interfaces             │
│   - No framework dependencies   │
└─────────────────────────────────┘
              ↑ implements
      ┌───────┴────────┐
      │                │
┌─────────────┐  ┌─────────────┐
│ io.eventbob │  │ io.eventbob │  Implementations (framework-specific)
│   .spring   │  │ .dropwizard │
│             │  │   (future)  │
│ Spring Boot │  │ Dropwizard  │
│ Spring JDBC │  │ JDBI        │
└─────────────┘  └─────────────┘
```

**Dependency Rule:** Implementations depend on core, never reverse.

---

## Macrolith-Based Registration Model

### What Registry Tracks

```
Macrolith: "messages-service"
├─ Endpoint: "http://messages-service" (logical URL, not IP)
├─ Capability: READ /content (GET)
├─ Capability: WRITE /content (POST)
└─ Capability: DELETE /content/{id} (DELETE)
```

Infrastructure resolves "http://messages-service" → physical instances:
- 10.0.1.5:8080
- 10.0.1.6:8080
- 10.0.1.7:8080

**Key insight:** Registry sees macroliths (logical units), not instances (physical servers).

### Three-Table Model

```sql
service_macroliths        -- Which macroliths exist
service_capabilities  -- Which capabilities exist
macrolith_capabilities    -- Which macroliths provide which capabilities (many-to-many)
```

**Rationale:**
- Capabilities can exist before macroliths register
- Macroliths can provide multiple capabilities (composition)
- Capabilities can be provided by multiple macroliths (replication)

---

## Component Diagram

```
┌─────────────────────────────────────────┐
│  Macrolith Process                      │
│  (e.g., "messages-service")             │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  EventBob Server               │    │
│  │                                │    │
│  │  [JAR Loader]                  │    │
│  │     ↓                          │    │
│  │  messages-read.jar             │    │
│  │  messages-write.jar            │    │
│  │  messages-delete.jar           │    │
│  │     ↓                          │    │
│  │  [Router] → in-process calls   │    │
│  └────────────────────────────────┘    │
│                                         │
│  Exposed as: "messages-service"        │
└─────────────────────────────────────────┘
             ↑
             │ (service discovery)
             │
     ┌───────────────┐
     │  Registry     │
     │  (PostgreSQL) │
     └───────────────┘
```

---

## io.eventbob.spring Implementation

### What It Provides

Spring Boot integration layer for EventBob server deployment. Provides transport adapters and infrastructure wiring.

**Implementation is work-in-progress** and evolving as requirements clarify.


---

## Open Questions About Architecture

Things I don't understand yet:

1. **How does routing work?** What mechanism routes requests inside the macrolith?
2. **JAR isolation?** Are services loaded in isolated classloaders?
3. **Transport layer?** User said "not just HTTP"—what transports are supported?
4. **Event model?** Is this event-driven? What are events?
5. **Bootstrap sequence?** What's the startup flow from JARs to running macrolith?
6. **Discovery mechanism?** How does macrolith register with registry on startup?

---

## What Belongs Where

### Core (`io.eventbob.core`)
- Domain concepts (Capability, Endpoint)
- Port interfaces (CapabilityResolver)
- **NO framework dependencies**

### Implementations (`io.eventbob.spring`)
- JAR scanning, persistence, bootstrap
- Framework-specific code (Spring JDBC, PostgreSQL)
- Implements core ports

### Rule
Core defines WHAT (domain model). Implementations define HOW (with specific frameworks).

---

## Dependency Rules (Enforced by ArchUnit)

**✅ Allowed:**
- Implementations → Core
- Adapters → Core
- Services → Core

**❌ Forbidden:**
- Core → Implementations
- Core → Adapters
- Implementation A → Implementation B
