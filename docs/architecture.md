# EventBob Architecture

## System Purpose

EventBob bundles multiple microservices into a macrolith to solve the chatty-application anti-pattern. When too many microservices cause network saturation and poor performance, EventBob loads their JARs into a single server for in-process communication.

---

## Three-Layer Architecture

EventBob follows Clean Architecture with three layers:

```
┌──────────────────────────────────────┐
│   io.eventbob.core                   │  Domain Layer (Innermost)
│   - Domain model (Event)             │
│   - Abstractions (EventHandler)      │
│   - JAR loading (HandlerLoader)      │
│   - Routing (EventBob)               │
│   - NO framework dependencies        │
└──────────────────────────────────────┘
              ↑ implements
              │
┌──────────────────────────────────────┐
│   io.eventbob.spring                 │  Infrastructure Library (Middle)
│   - Spring Boot wiring               │
│   - HTTP transport adapters          │
│   - EventBobConfig (generic)         │
│   - NO main class (library)          │
│   - NO hard-coded paths              │
└──────────────────────────────────────┘
              ↑ imports & configures
              │
┌──────────────────────────────────────┐
│   io.eventbob.example.macrolith.*   │  Application Layer (Outermost)
│   - Spring Boot main class           │
│   - Concrete JAR path configuration  │
│   - Deployment-specific wiring       │
│   - Example: macrolith.echo          │
└──────────────────────────────────────┘
```

**Dependency Rule:** Each layer depends only on layers inside it. Applications depend on infrastructure library, which depends on core. Core depends on nothing (except JDK).

**Why three layers:**
- **Core:** Framework-agnostic domain logic (what EventBob IS)
- **Spring library:** Reusable infrastructure (how to deploy with Spring Boot)
- **Macrolith applications:** Concrete configurations (which handlers to load)

---

## Dependency Inversion at Boundaries

### Core → Spring Boundary

Core defines HandlerLoader interface with factory method:

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
    private final List<Path> handlerJarPaths;
    
    public EventBobConfig(List<Path> handlerJarPaths) {
        this.handlerJarPaths = handlerJarPaths;
    }
    
    private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();  // Factory method
        return loader.loadHandlers(handlerJarPaths);
    }
}
```

**Key insight:** Spring never sees JarHandlerLoader (package-private). It depends only on the public HandlerLoader interface. This is Dependency Inversion Principle in practice.

### Spring → Application Boundary

Spring library defines EventBobConfig that accepts configuration via constructor injection:

```java
// In EventBobConfig (io.eventbob.spring) - generic library
public EventBobConfig(List<Path> handlerJarPaths) {
    this.handlerJarPaths = handlerJarPaths;
}
```

Application provides concrete configuration:

```java
// In EchoMacrolithApplication (io.eventbob.example.macrolith.echo) - concrete app
@Bean
public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar"),
        Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar")
    );
}
```

**Key insight:** Spring library has no hard-coded paths, no main class. It is purely infrastructure. Applications decide which handlers to load.

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

### Spring (Library Structure)

```
io.eventbob.spring
├── EventBobConfig                 (Spring configuration - constructor injection)
├── EventController                (REST endpoint adapter)
├── EventDto                       (HTTP/Event boundary DTO)
└── handlers/
    └── HealthcheckHandler         (Built-in system handler)
```

**No main class.** This is a library module. Applications import and configure it.

### Macrolith Applications (Example: Echo)

```
io.eventbob.example.macrolith.echo
└── EchoMacrolithApplication       (Spring Boot main class + JAR path config)
```

More macrolith applications will follow this pattern (e.g., `io.eventbob.example.macrolith.upper` for testing remote invocation).

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
│  (e.g., echo-macrolith)                 │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  EventBob Server (Spring)      │    │
│  │                                │    │
│  │  [HandlerLoader]               │    │
│  │     ↓                          │    │
│  │  echo.jar                      │    │
│  │  lower.jar                     │    │
│  │     ↓                          │    │
│  │  [EventBob Router]             │    │
│  │     → in-process calls         │    │
│  └────────────────────────────────┘    │
│                                         │
│  Configured in:                         │
│  EchoMacrolithApplication               │
└─────────────────────────────────────────┘
             ↑
             │ (future: service discovery)
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
Macrolith: "echo-macrolith"
├─ Endpoint: "http://echo-macrolith" (logical URL, not IP)
├─ Capability: echo /events (POST)
└─ Capability: lower /events (POST)
```

Infrastructure resolves "http://echo-macrolith" → physical instances:
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
- `io.eventbob.example.macrolith.*` → `io.eventbob.spring`
- `io.eventbob.spring` → `io.eventbob.core`
- Applications → Library → Domain

**❌ Forbidden:**
- `io.eventbob.core` → `io.eventbob.spring`
- `io.eventbob.spring` → `io.eventbob.example.*`
- Domain → Infrastructure
- Library → Applications

**Enforcement mechanism:** ArchUnit tests in both modules verify dependency direction.

---

## What Belongs Where

### Core (`io.eventbob.core`)
- Domain concepts (Event, EventHandler, Capability)
- Routing abstractions (Dispatcher)
- JAR loading abstractions and implementation (HandlerLoader, JarHandlerLoader)
- **NO framework dependencies** (Spring, Dropwizard, etc.)

### Spring Library (`io.eventbob.spring`)
- Generic EventBob configuration (constructor injection)
- HTTP transport adapters
- REST endpoints
- Framework-specific code (Spring Boot, Spring Web)
- **NO main class, NO hard-coded paths**

### Macrolith Applications (`io.eventbob.example.macrolith.*`)
- Spring Boot main class
- Concrete JAR path configuration
- Deployment-specific wiring
- Runtime dependencies on handler JARs

### Rule
Core defines WHAT (domain model + abstractions). Infrastructure library defines HOW (with specific frameworks). Applications define WHICH (which handlers to load, where to deploy).

---

## Multiple Macrolith Deployments

The three-layer architecture enables multiple concrete macrolith deployments from a single infrastructure library:

```
                io.eventbob.core
                       ↑
                       │
                io.eventbob.spring
                       ↑
         ┌─────────────┼─────────────┐
         │             │             │
   macrolith.echo  macrolith.upper  macrolith.messages
   (lower + echo)  (upper + ...)   (read + write + ...)
```

Each macrolith:
- Imports io.eventbob.spring library
- Provides @Bean for List<Path> handlerJarPaths
- Has its own Spring Boot main class
- Loads different combinations of handlers

The infrastructure library remains unchanged regardless of which handlers are loaded.

---

## Future: Additional Infrastructure Implementations

The architecture also allows for alternative infrastructure implementations beyond Spring Boot:

```
                io.eventbob.core
                       ↑
         ┌─────────────┼─────────────┐
         │             │             │
io.eventbob.spring  io.eventbob.dropwizard  io.eventbob.quarkus
   (current)           (future)              (future)
```

Each infrastructure library:
- Depends on core
- Never depends on other infrastructure modules
- Provides framework-specific wiring
- Uses HandlerLoader.jarLoader() factory method

Core remains unchanged regardless of infrastructure choice.
