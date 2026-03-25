# Core Module Architecture (io.eventbob.core)

## Purpose

The core module contains the domain model and abstractions for EventBob. It is framework-agnostic. It defines WHAT EventBob is, not HOW it is deployed.

Core has no dependencies beyond the JDK. No Spring. No Dropwizard. No HTTP clients. No YAML parsers. This keeps the domain pure and testable.

---

## Layer Assignment

**Domain Layer (innermost):**
- Event (domain model)
- EventHandler (core abstraction)
- EventBob (routing logic)
- Dispatcher (dispatch contract)
- HandlerLifecycle (lifecycle contract)
- LifecycleContext (initialization context contract)
- Capability (annotation)
- Capabilities (vocabulary constants)
- Exceptions (EventHandlingException, HandlerNotFoundException)

**Adapters (internal, package-private):**
- JarHandlerLoader (JAR → EventHandler adapter for POJOs)
- LifecycleHandlerLoader (JAR → EventHandler adapter for lifecycle handlers)
- DiscoveredHandler (internal structure for tracking capability metadata)
- DefaultErrorEvent (error event adapter)
- LifecycleContextImpl (implementation of LifecycleContext interface)

These adapters live in the core because they bridge the JDK (URLClassLoader, reflection) to the domain (EventHandler). They do not depend on external frameworks. They are package-private because external code should use the HandlerLoader factory methods, not construct them directly.

---

## Public API Surface

**Classes and interfaces that other modules depend on:**

| Type | Visibility | Purpose |
|------|-----------|---------|
| Event | public | Domain model for events |
| EventHandler | public | Core abstraction for event processing |
| EventBob | public | Router for dispatching events to handlers |
| Dispatcher | public | Contract for sending events to capabilities |
| HandlerLifecycle | public | Lifecycle contract for handler initialization |
| LifecycleContext | public | Context provided to handlers during initialization |
| HandlerLoader | public | Abstraction for loading handlers from various sources |
| Capability | public | Annotation for declaring handler capabilities |
| Capabilities | public | Vocabulary constants for common capabilities |
| EventHandlingException | public | Checked exception for handler failures |
| HandlerNotFoundException | public | Runtime exception when capability not found |

**Internal implementations (package-private):**

| Type | Visibility | Purpose |
|------|-----------|---------|
| JarHandlerLoader | package-private | POJO handler loading from JARs |
| LifecycleHandlerLoader | package-private | Lifecycle handler loading from JARs |
| LifecycleContextImpl | package-private | Implementation of LifecycleContext |
| DiscoveredHandler | package-private | Internal structure for capability metadata |
| DefaultErrorEvent | package-private | Error event construction |

---

## Two Loading Strategies

HandlerLoader provides two factory methods:

### Strategy 1: jarLoader (POJO Handlers)

```
HandlerLoader.jarLoader(Collection<Path> jarPaths)
    ↓
JarHandlerLoader (package-private)
    ↓ scans JARs for
EventHandler implementations with @Capability
    ↓ calls
Class.getDeclaredConstructor().newInstance() (no-arg constructor)
    ↓
Map<String, EventHandler>
```

**Use case:** Simple handlers with no dependencies, no configuration, no startup/shutdown requirements.

### Strategy 2: lifecycleLoader (Full Microservice Handlers)

```
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher)
    or
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher, Object frameworkContext)
    ↓
LifecycleHandlerLoader (package-private)
    ↓ reads
META-INF/eventbob-handler.properties (lifecycle.class property)
    ↓ loads
application.yml (configuration - TODO: not yet implemented)
    ↓ instantiates
HandlerLifecycle subclass (no-arg constructor)
    ↓ calls
lifecycle.initialize(LifecycleContext)
    ↓ calls
lifecycle.getHandler()
    ↓
EventHandler (fully initialized with dependencies)
    ↓
Map<String, EventHandler>
```

**Use case:** Handlers that need configuration, dependencies (DataSource, HTTP clients), framework integration (Spring, Dropwizard), or startup/shutdown hooks.

Both strategies produce the same result: `Map<String, EventHandler>`. The EventBob router does not know or care which strategy loaded a handler.

---

## Dependency Inversion at Boundaries

### External Modules → Core

```
io.eventbob.spring      → HandlerLoader (interface)
                        → EventHandler (interface)
                        → Event (class)
                        → Capability (annotation)
                        → HandlerLifecycle (abstract class)
                        → LifecycleContext (interface)

io.eventbob.example.echo → EventHandler (interface)
                         → HandlerLifecycle (abstract class)
                         → LifecycleContext (interface)
                         → Capability (annotation)
```

External modules depend only on public interfaces and abstractions. They never see JarHandlerLoader, LifecycleHandlerLoader, or DiscoveredHandler (all package-private).

### Core → JDK Only

Core has no outward dependencies. It depends only on:
- `java.nio.file.*` (for Path)
- `java.net.*` (for URLClassLoader)
- `java.lang.reflect.*` (for reflection)
- `java.util.*` (for collections)
- `java.util.logging.*` (for logging)

No Spring. No Jackson. No SnakeYAML. No HTTP clients.

---

## Resource Management

HandlerLoader extends `AutoCloseable`. This enables try-with-resources and ensures cleanup:

```java
try (HandlerLoader loader = HandlerLoader.lifecycleLoader(jarPaths, dispatcher)) {
    Map<String, EventHandler> handlers = loader.loadHandlers();
    // Use handlers...
} // loader.close() called automatically
```

**What close() does:**

### For JarHandlerLoader (POJOs)
- No resources to clean up
- Empty implementation (no-op)

### For LifecycleHandlerLoader
1. Call `lifecycle.shutdown()` on all tracked lifecycles
   - Handlers close database connections, shutdown thread pools, close Spring contexts, etc.
2. Close all URLClassLoaders
   - Releases class loader resources

**Ordering matters:** Lifecycles are shut down BEFORE class loaders are closed. This allows handlers to reference classes during shutdown (e.g., Spring context close needs to access bean classes).

---

## Framework-Agnostic Context Mechanism

LifecycleContext provides optional access to framework-specific resources via `getFrameworkContext<T>(Class<T> type)`.

**How it works:**

1. **Core defines the abstraction** (LifecycleContext interface with generic method)
2. **Core provides a package-private implementation** (LifecycleContextImpl)
3. **Infrastructure layer optionally provides framework context** (e.g., Spring microlith passes ApplicationContext)
4. **Handler optionally uses it** (e.g., Spring-based handler requests ApplicationContext)

**Example:**

```java
// In Spring microlith (infrastructure layer)
HandlerLoader loader = HandlerLoader.lifecycleLoader(
    jarPaths, 
    dispatcher, 
    applicationContext  // Spring's ApplicationContext passed here
);

// In handler JAR (isolated, no framework dependency)
public void initialize(LifecycleContext context) {
    Optional<ApplicationContext> spring = 
        context.getFrameworkContext(ApplicationContext.class);
    
    if (spring.isPresent()) {
        // Use parent Spring context
    } else {
        // Manual wiring
    }
}
```

**Key insight:** Core has no dependency on Spring. The generic type parameter (`<T>`) preserves type safety while maintaining framework-agnosticism. The handler JAR can depend on Spring if it wants to, but it does not have to. The core does not care.

---

## Visibility Boundaries

**Why package-private implementations?**

JarHandlerLoader and LifecycleHandlerLoader are package-private because external code should use the HandlerLoader factory methods, not construct loaders directly.

**The pattern:**

```java
// ✅ Correct (uses public factory method)
HandlerLoader loader = HandlerLoader.jarLoader(jarPaths);

// ❌ Wrong (implementation detail, not accessible)
JarHandlerLoader loader = new JarHandlerLoader(jarPaths);
```

This is the Static Factory Method pattern. The interface exposes the contract. The factory method decides which implementation to return. The implementation remains hidden. This allows the implementation to change without breaking external code.

**Public abstractions, package-private implementations.** This is the Dependency Inversion Principle at the package level.

---

## Component Diagram

```
┌────────────────────────────────────────────────────┐
│  io.eventbob.core (domain layer)                   │
│                                                     │
│  PUBLIC API:                                        │
│  ┌──────────────────────────────────────────────┐ │
│  │ Event                                        │ │
│  │ EventHandler                                 │ │
│  │ EventBob (router)                            │ │
│  │ Dispatcher                                   │ │
│  │ HandlerLifecycle (abstract)                  │ │
│  │ LifecycleContext (interface)                 │ │
│  │ HandlerLoader (interface + factory methods)  │ │
│  │ Capability, Capabilities                     │ │
│  │ Exceptions                                   │ │
│  └──────────────────────────────────────────────┘ │
│                 ↑                                   │
│                 │ depends on                        │
│  PACKAGE-PRIVATE IMPLEMENTATIONS:                  │
│  ┌──────────────────────────────────────────────┐ │
│  │ JarHandlerLoader (POJO loading)              │ │
│  │ LifecycleHandlerLoader (lifecycle loading)   │ │
│  │ LifecycleContextImpl                         │ │
│  │ DiscoveredHandler                            │ │
│  │ DefaultErrorEvent                            │ │
│  └──────────────────────────────────────────────┘ │
│                                                     │
│  DEPENDS ON: JDK only (java.nio, java.net,         │
│              java.lang.reflect, java.util)          │
└────────────────────────────────────────────────────┘
              ↑
              │ depends on public API only
              │
┌─────────────────────────────────────────────────────┐
│  External modules (Spring, examples, microliths)    │
└─────────────────────────────────────────────────────┘
```

---

## Current State and Future Work

**Implemented:**
- HandlerLifecycle abstract class
- LifecycleContext interface
- LifecycleHandlerLoader implementation
- Factory methods in HandlerLoader
- Resource management via AutoCloseable
- Framework-agnostic context mechanism

**Not Yet Implemented:**
- YAML configuration parsing (LifecycleContext.getConfiguration() returns empty map)
- Environment variable substitution in configuration
- Configuration validation

**Design Decision: Defer YAML parsing to avoid dependency on SnakeYAML or Jackson in core.**

Future options:
1. Add YAML library dependency to core (simple, but adds dependency)
2. Pass configuration parser as parameter to lifecycleLoader (clean, but complex API)
3. Provide configuration externally to microlith, which passes it to loader (flexible, defers decision)

This decision is deferred. The lifecycle contract is in place. Configuration mechanism can be added later without breaking existing handlers.

---

## Summary

The core module defines the domain contracts and provides two loading strategies: simple POJO loading and lifecycle-based loading for full microservices. External modules depend only on public abstractions. Implementations are package-private and accessed via factory methods. The module has no dependencies beyond the JDK.

This structure keeps the core stable, testable, and framework-agnostic while supporting handlers ranging from simple POJOs to full Spring-based microservices.
