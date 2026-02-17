# EventBob Architecture

## System Purpose

EventBob bundles multiple microservices into a macrolith to solve the chatty-application anti-pattern. When too many microservices cause network saturation and poor performance, EventBob loads their JARs into a single server for in-process communication.

---

## Two-Layer Architecture

EventBob follows Clean Architecture with two layers:

```
┌─────────────────────────────────┐
│   io.eventbob.core              │  Domain Layer (Innermost)
│   - Domain model (Event)        │
│   - Abstractions (EventHandler) │
│   - JAR loading (HandlerLoader) │
│   - Routing (EventBob)          │
│   - NO framework dependencies   │
└─────────────────────────────────┘
              ↑ implements
              │
┌─────────────────────────────────┐
│   io.eventbob.spring            │  Infrastructure Layer (Outermost)
│   - Spring Boot wiring          │
│   - HTTP transport adapters     │
│   - Concrete handlers           │
│   - Framework-specific code     │
└─────────────────────────────────┘
```

**Dependency Rule:** Infrastructure depends on core. Core depends on nothing (except JDK).

**Why loader is in core:** JAR loading and ClassLoader manipulation are Java domain concepts, not infrastructure concerns. The HandlerLoader abstraction belongs in the domain because it defines WHAT handler loading means. The implementation (JarHandlerLoader) uses only JDK classes (URLClassLoader, JarFile) - no framework dependencies.

---

## Dependency Inversion at the Boundary

Core defines the HandlerLoader interface with a factory method:

```java
public interface HandlerLoader {
    Map<String, EventHandler> loadHandlers(Collection<Path> jarPaths) throws IOException;
    
    static HandlerLoader jarLoader() {
        return new JarHandlerLoader();  // package-private implementation
    }
}
```

Spring infrastructure depends only on the interface:

```java
@Configuration
public class EventBobConfig {
    private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();  // Factory method
        return loader.loadHandlers(jarPaths);
    }
}
```

**Key insight:** Spring never sees JarHandlerLoader (package-private). It depends only on the public HandlerLoader interface. This is Dependency Inversion Principle in practice.

---

## Package Structure

### Core (Flat Structure)

All classes live in `io.eventbob.core` - no subpackages:

```
io.eventbob.core
├── Event                          (public - domain model)
├── EventHandler                   (public - core abstraction)
├── EventBob                       (public - router)
├── Dispatcher                     (public - dispatch contract)
├── HandlerLoader                  (public - loading abstraction)
├── JarHandlerLoader              (package-private - loading implementation)
├── DiscoveredHandler             (package-private - internal structure)
├── DefaultErrorEvent             (package-private - error handling)
├── Capability                     (public - annotation)
├── Capabilities                   (public - vocabulary)
├── EventHandlingException         (public - checked exception)
└── HandlerNotFoundException       (public - runtime exception)
```

**Visibility boundaries:**
- **Public API:** Event, EventHandler, EventBob, Dispatcher, HandlerLoader, Capability, Capabilities, exceptions
- **Package-private internals:** JarHandlerLoader, DiscoveredHandler, DefaultErrorEvent

**Rationale for flat structure:** Core is small and cohesive. All classes operate at the same abstraction level (domain primitives and routing). Subpackages would add complexity without clarity gains.

### Spring (Adapter Structure)

```
io.eventbob.spring
├── EventBobConfig                 (Spring configuration)
├── EventBobApplication            (Spring Boot entry point)
└── handlers/
    └── HealthcheckHandler         (Built-in system handler)
```

---

## JAR Loading Strategy

HandlerLoader uses isolated class loaders:

**Pattern:** One URLClassLoader per JAR
- **Parent delegation:** Core EventBob classes shared across all handlers
- **JAR isolation:** Each JAR's dependencies isolated from others
- **Discovery:** Scans for classes annotated with @Capability that implement EventHandler
- **Instantiation:** Reflective invocation of no-args constructor

**Error handling:**
- Missing JAR: IOException (fail fast)
- Malformed JAR: Warning logged, JAR skipped
- Duplicate capabilities: IllegalStateException (fail fast)
- Class loading failures: Fine-level log, class skipped
- Instantiation failures: IllegalStateException (fail fast)

---

## Component Diagram

```
┌─────────────────────────────────────────┐
│  Macrolith Process                      │
│  (e.g., "messages-service")             │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  EventBob Server (Spring)      │    │
│  │                                │    │
│  │  [HandlerLoader]               │    │
│  │     ↓                          │    │
│  │  messages-read.jar             │    │
│  │  messages-write.jar            │    │
│  │  messages-delete.jar           │    │
│  │     ↓                          │    │
│  │  [EventBob Router]             │    │
│  │     → in-process calls         │    │
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
service_macroliths       -- Which macroliths exist
service_capabilities     -- Which capabilities exist
macrolith_capabilities   -- Which macroliths provide which capabilities (many-to-many)
```

**Rationale:**
- Capabilities can exist before macroliths register
- Macroliths can provide multiple capabilities (composition)
- Capabilities can be provided by multiple macroliths (replication)

---

## Dependency Rules (Enforced by ArchUnit)

**✅ Allowed:**
- `io.eventbob.spring` → `io.eventbob.core`
- Infrastructure → Domain

**❌ Forbidden:**
- `io.eventbob.core` → `io.eventbob.spring`
- Domain → Infrastructure
- Core importing Spring types

**Enforcement mechanism:** ArchUnit tests in both modules verify dependency direction.

---

## Open Questions About Architecture

Things that need clarification:

1. **How does routing work?** What mechanism routes requests inside the macrolith?
2. **Transport layer?** User said "not just HTTP"—what transports are supported?
3. **Event model?** Is this event-driven? What are events vs. requests?
4. **Bootstrap sequence?** What's the startup flow from JARs to running macrolith?
5. **Discovery mechanism?** How does macrolith register with registry on startup?

---

## What Belongs Where

### Core (`io.eventbob.core`)
- Domain concepts (Event, EventHandler, Capability)
- Routing abstractions (Dispatcher)
- JAR loading abstractions and implementation (HandlerLoader, JarHandlerLoader)
- **NO framework dependencies** (Spring, Dropwizard, etc.)

### Spring (`io.eventbob.spring`)
- JAR scanning configuration (paths, patterns)
- Persistence, bootstrap
- Framework-specific code (Spring JDBC, PostgreSQL)
- HTTP transport adapters (future)

### Rule
Core defines WHAT (domain model + abstractions). Infrastructure defines HOW (with specific frameworks) and WHEN (configuration, startup).

---

## Future: Additional Infrastructure Implementations

The two-layer architecture allows for alternative infrastructure implementations:

```
                io.eventbob.core
                       ↑
         ┌─────────────┼─────────────┐
         │             │             │
io.eventbob.spring  io.eventbob.dropwizard  io.eventbob.quarkus
   (current)           (future)              (future)
```

Each infrastructure module:
- Depends on core
- Never depends on other infrastructure modules
- Provides framework-specific wiring
- Uses HandlerLoader.jarLoader() factory method

Core remains unchanged regardless of infrastructure choice.
