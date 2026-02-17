# EventBob Architecture

## System Purpose

EventBob bundles multiple microservices into a microlith to solve the chatty-application anti-pattern. When too many microservices cause network saturation and poor performance, EventBob loads their JARs into a single server for in-process communication.

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
│   io.eventbob.example.microlith.*   │  Application Layer (Outermost)
│   - Spring Boot main class           │
│   - Concrete JAR path configuration  │
│   - Deployment-specific wiring       │
│   - Example: microlith.echo          │
└──────────────────────────────────────┘
```

**Dependency Rule:** Each layer depends only on layers inside it. Applications depend on infrastructure library, which depends on core. Core depends on nothing (except JDK).

**Why three layers:**
- **Core:** Framework-agnostic domain logic (what EventBob IS)
- **Spring library:** Reusable infrastructure (how to deploy with Spring Boot)
- **Microlith applications:** Concrete configurations (which handlers to load)

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
// In EchoApplication (io.eventbob.example.microlith.spring.echo) - concrete app
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

### Microlith Applications (Example: Echo with Spring)

```
io.eventbob.example.microlith.spring.echo
└── EchoApplication                 (Spring Boot main class + JAR path config)
```

More microlith applications will follow this pattern (e.g., `io.eventbob.example.microlith.spring.upper` for testing remote invocation, or `io.eventbob.example.microlith.dropwizard.echo` for alternative framework).

---

## Component Diagram

```
┌─────────────────────────────────────────┐
│  Microlith Process                      │
│  (e.g., echo-microlith)                 │
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
│  EchoApplication                        │
└─────────────────────────────────────────┘
```

---

## Dependency Rules

**✅ Allowed:**
- `io.eventbob.example.microlith.*` → `io.eventbob.spring`
- `io.eventbob.spring` → `io.eventbob.core`
- Applications → Library → Domain

**❌ Forbidden:**
- `io.eventbob.core` → `io.eventbob.spring`
- `io.eventbob.spring` → `io.eventbob.example.*`
- Domain → Infrastructure
- Library → Applications

**Enforcement mechanism:** Maven module boundaries provide structural enforcement. Automated tests planned.

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

### Microlith Applications (`io.eventbob.example.microlith.*`)
- Spring Boot main class
- Concrete JAR path configuration
- Deployment-specific wiring
- Runtime dependencies on handler JARs

### Rule
Core defines WHAT (domain model + abstractions). Infrastructure library defines HOW (with specific frameworks). Applications define WHICH (which handlers to load, where to deploy).

---

## Multiple Microlith Deployments

The three-layer architecture enables multiple concrete microlith deployments from a single infrastructure library:

```
                io.eventbob.core
                       ↑
                       │
                io.eventbob.spring
                       ↑
         ┌─────────────┼─────────────┐
         │             │             │
   microlith.echo  microlith.upper  microlith.messages
   (lower + echo)  (upper + ...)   (read + write + ...)
```

Each microlith:
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
