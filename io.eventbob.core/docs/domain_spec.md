# io.eventbob.core Domain Specification

## Bounded Context

**io.eventbob.core** is the single bounded context for EventBob. This module defines the domain model, core concepts, and port interfaces. There are no separate bounded contexts - EventBob uses one ubiquitous language throughout.

Infrastructure modules (io.eventbob.spring, etc.) are adapters that implement core ports. They use the same ubiquitous language, not a different semantic model.

---

## Aggregate Roots

### EventBob (Router)
The central aggregate that routes events to registered handlers. Manages the capability-to-handler mapping and orchestrates event dispatch.

**Responsibilities:**
- Register handlers for capabilities (from HandlerLoader)
- Route events to correct handler based on Event.getTarget()
- Enforce capability uniqueness within microlith
- Provide location-transparent routing (local and remote handlers treated identically)

**Invariants:**
- Each capability must map to exactly one handler
- No capability can be registered twice
- Routing must be deterministic (same capability always routes to same handler)

### HandlerLifecycle (Container Contract)
The lifecycle contract between EventBob container and handlers. Like Servlet.init/destroy, this defines how handlers integrate with the container.

**Responsibilities:**
- initialize(LifecycleContext): Prepare handler with dependencies and configuration
- getHandler(): Return the initialized EventHandler for event processing
- shutdown(): Release resources (database connections, Spring contexts, thread pools)

**Invariants:**
- initialize() must complete successfully before getHandler() is called
- getHandler() must return a non-null EventHandler
- shutdown() must be idempotent (safe to call multiple times)
- Lifecycle implementation must not hold framework dependencies in core (framework-agnostic)

**Domain truth:** HandlerLifecycle is a domain concept (container contract), not infrastructure. It defines WHAT the contract is, not HOW handlers wire themselves.

---

## Domain Concepts

### Implemented Concepts

#### HandlerLifecycle
Lifecycle contract for handler initialization and cleanup. This is how handlers integrate with the EventBob container when they need initialization (database connections, Spring contexts, configuration).

**Pattern:** Abstract class for evolvability. Future versions can add lifecycle methods (health checks, metrics) with default implementations without breaking existing handlers.

**Framework-agnostic:** Core defines the contract. Implementations can use Spring, Dropwizard, Guice, manual wiring, or any approach. Container doesn't know or care how handlers wire themselves.

**Three phases:**
1. **initialize(LifecycleContext)**: Handler sets itself up using provided context
2. **getHandler()**: Container retrieves initialized handler
3. **shutdown()**: Handler cleans up resources

**When to use:** Handlers that need configuration, dependencies, or framework integration. Simple POJO handlers can skip lifecycle.

#### LifecycleContext
Context provided to handlers during initialization. Contains everything a handler needs:

**Fields:**
- **configuration (Map<String, Object>)**: Handler-specific config from application.yml
- **dispatcher (Dispatcher)**: For sending events to other capabilities
- **frameworkContext (Optional<T>)**: Framework-specific context (Spring ApplicationContext, Dropwizard Environment, etc.)

**Design principle:** Framework-agnostic extension point. Core EventBob has no framework dependencies. Handlers can optionally use framework context if available, or wire manually if not.

**Current state:** Configuration loading from application.yml is **not yet implemented**. `getConfiguration()` returns empty map. YAML parsing is TODO. Handlers must not depend on configuration until implemented.

#### HandlerLoader
Port interface for loading handlers from various sources (JARs, remote endpoints). Defines the contract for handler discovery and instantiation.

**Contract:**
- `Map<String, EventHandler> loadHandlers()`: Discover and instantiate handlers, return capability-to-handler mapping
- `void close()`: Release resources (class loaders, lifecycles, HTTP clients)

**Resource management:** Extends AutoCloseable. Implementations that manage resources must release them in close(). EventBob calls close() on shutdown.

**Two strategies:**

1. **JarHandlerLoader (POJO loading)**:
   - Simple handlers with no-arg constructors
   - No lifecycle, no DI, no configuration
   - Scans JARs for EventHandler classes with @Capability
   - Instantiates via Class.newInstance()
   - No resources to clean up (close() is no-op)

2. **LifecycleHandlerLoader (Lifecycle loading)**:
   - Full microservice handlers with initialization needs
   - Reads META-INF/eventbob-handler.properties to find lifecycle class
   - Loads configuration from application.yml (not yet implemented - returns empty map)
   - Instantiates HandlerLifecycle, calls initialize(context), retrieves handler
   - Tracks lifecycles and class loaders for cleanup
   - close() calls shutdown() on all lifecycles, then closes all class loaders

**Factory methods:**
- `HandlerLoader.jarLoader(Collection<Path>)`: POJO loading
- `HandlerLoader.lifecycleLoader(jarPaths, dispatcher)`: Lifecycle loading without framework context
- `HandlerLoader.lifecycleLoader(jarPaths, dispatcher, frameworkContext)`: Lifecycle loading with framework context

#### EventHandler
Integration contract for event processing. Single method: `Event handle(Event, Dispatcher)`.

**Responsibilities:**
- Process event for declared capability
- Use dispatcher to invoke other capabilities if needed
- Return response event (success or error)

**Discovery:** Annotated with @Capability to declare what the handler provides.

#### Capability
Annotation that declares what a handler provides. Structure:
- `value: String` - capability identifier (e.g., "get-message-content")
- `version: int` - version of capability contract (default: 1)

**Repeatable:** Handlers can declare multiple capabilities via repeated @Capability annotations.

**Uniqueness:** Within a microlith, each capability identifier must be unique. Duplicate capabilities cause IllegalStateException during loading.

#### Event
Message envelope for in-process communication. Contains:
- source: String (capability that sent the event)
- target: String (capability to receive the event)
- parameters: Map<String, String> (routing metadata)
- metadata: Map<String, Object> (arbitrary data)
- payload: Object (message body)

**Immutable:** Events are immutable. Handlers create new events for responses.

**Not a domain event:** Despite the name, Event is a transport envelope (request/response wrapper), not an event in the Event Sourcing sense.

#### Dispatcher
Facility for sending events to other capabilities. Two send semantics:
- **Async**: `CompletableFuture<Event> send(Event, BiFunction<Throwable, Event, Event>)` - returns future immediately
- **Sync**: `Event send(Event, BiFunction<Throwable, Event, Event>, long)` - blocks until response or timeout

**Location transparency:** Dispatcher routes to local or remote handlers transparently. Handlers don't know or care where the target capability is located.

---

## Domain Invariants

1. **Capabilities are unique within microlith** — No two handlers can declare the same capability identifier
2. **Lifecycle before handler** — initialize() must complete before getHandler() is called
3. **Non-null handler** — getHandler() must return a non-null EventHandler instance
4. **Framework-agnostic lifecycle contract** — HandlerLifecycle abstract class has no framework dependencies (no Spring, Dropwizard, etc. in core)
5. **Container controls lifecycle** — EventBob calls initialize() and shutdown() at appropriate times. Handlers do not self-initialize.
6. **Shutdown is idempotent** — shutdown() must be safe to call multiple times
7. **Resource cleanup order** — Lifecycles shut down before class loaders close (handlers may reference classes during shutdown)
8. **Graceful degradation** — If one handler fails to load, other handlers continue loading (logged as warning)
9. **Lifecycle handlers require properties file** — JARs using LifecycleHandlerLoader must include META-INF/eventbob-handler.properties declaring lifecycle.class
10. **Configuration is optional** — Until YAML parsing is implemented, handlers must not depend on configuration (empty map provided)

---

## Bounded Context Boundaries

EventBob core is a single bounded context. Infrastructure modules are not separate bounded contexts - they are adapters implementing core ports.

### Core → Infrastructure (Dependency Direction)
Infrastructure depends on core, never the reverse.

**Correct:**
- io.eventbob.spring imports io.eventbob.core (HandlerLoader, EventHandler, Event)
- Spring types (ApplicationContext, RestTemplate) live in infrastructure layer
- Core has no Spring dependencies

**Incorrect:**
- Core imports Spring types (would violate Dependency Inversion Principle)
- Core knowledge of HTTP, REST, or external protocols

### Anti-Corruption Layers

**HTTP Boundary:**
- **EventDto** translates between domain Event (core) and JSON (infrastructure)
- Keeps Jackson annotations out of core Event class
- Infrastructure converts EventDto ↔ Event at system edge
- Core never sees EventDto

**Remote Capability Integration:**
- **HttpEventHandlerAdapter** wraps remote HTTP endpoints as EventHandler implementations
- Translates Event → HTTP request → HTTP response → Event
- Remote handlers are indistinguishable from local handlers at routing layer (location transparency)

---

## Ubiquitous Language (Module-Specific Terms)

This section documents terms specific to io.eventbob.core. For general EventBob vocabulary, see top-level docs/domain_spec.md.

### Container
EventBob's role as a lifecycle manager for handlers. Like Servlet containers or Spring containers, EventBob:
- Loads handler JARs with isolated class loaders
- Discovers handlers via @Capability annotations
- Calls initialize() before event processing
- Routes events to correct handlers
- Calls shutdown() on container shutdown

### Lifecycle Contract
The abstract class HandlerLifecycle that defines how handlers integrate with the container. Similar to Servlet interface (init/destroy) but tailored to EventBob's needs (configuration, dispatcher, framework context).

### Framework-Agnostic
Design principle: core EventBob has no framework dependencies. Handlers can use any framework (Spring, Dropwizard, Guice, manual wiring) without core knowing or caring. LifecycleContext.getFrameworkContext() provides extension point without coupling core to frameworks.

### Isolated Class Loader
Each handler JAR loads into its own URLClassLoader with core classes as parent. This provides dependency isolation - handlers can use different versions of libraries without conflicts.

### Graceful Degradation
Error handling strategy: if one handler fails to load, log warning and continue with other handlers. Microlith remains operational with available capabilities rather than failing completely.

---

## Known Limitations (Current Implementation State)

### Configuration Loading Not Implemented
**Status:** LifecycleContext.getConfiguration() returns empty map. YAML parsing is TODO.

**Impact:** Handlers cannot yet load configuration from application.yml. They must hardcode configuration or obtain it from framework context.

**Invariant during limitation:** Handlers must not depend on configuration. When YAML parsing is implemented, this invariant will be lifted.

**Evidence:** LifecycleHandlerLoader line 229-233 has TODO comment and returns Map.of().

### No Health Check Support
**Status:** HandlerLifecycle has no health check method.

**Future:** Abstract class pattern allows adding health checks without breaking existing implementations.

### No Configuration Reload
**Status:** Configuration is loaded once during initialize(). No hot-reload support.

**Future:** Abstract class pattern allows adding reload() method without breaking existing implementations.

---

## Context Boundary Contracts

Since EventBob is a single bounded context, there are no context-to-context translation requirements. However, there are boundary contracts at infrastructure integration points:

### JAR Loading Boundary
**Contract:** Handler JARs provide either:
1. EventHandler class with @Capability and no-arg constructor (POJO loading), OR
2. META-INF/eventbob-handler.properties declaring lifecycle.class (lifecycle loading)

**Enforcement:** JARs without proper structure are skipped with warning (graceful degradation).

### Lifecycle Initialization Boundary
**Contract:** 
- LifecycleHandlerLoader calls initialize(context)
- Handler prepares itself using context (config, dispatcher, framework context)
- Handler returns non-null EventHandler via getHandler()

**Failure modes:**
- initialize() throws exception: handler not registered, logged as warning
- getHandler() returns null: IllegalStateException, handler not registered

### Shutdown Boundary
**Contract:**
- EventBob calls close() on HandlerLoader
- Loader calls shutdown() on all tracked HandlerLifecycle instances
- Loader closes all URLClassLoader instances
- Order: lifecycles before class loaders (handlers may reference classes during shutdown)

**Failure modes:**
- shutdown() throws exception: logged as warning, other handlers continue shutting down
- close() on classloader throws IOException: logged as warning, other loaders continue closing

---

## Future Evolution

### Planned Enhancements (Not Yet Implemented)

1. **YAML Configuration Parsing**:
   - Parse application.yml from handler JARs
   - Provide typed Map<String, Object> via LifecycleContext.getConfiguration()
   - Support environment variable substitution (${DB_PASSWORD})

2. **Health Checks**:
   - Add healthCheck(): HealthStatus method to HandlerLifecycle
   - Default implementation returns healthy
   - Handlers can override to report readiness (database connected, etc.)

3. **Configuration Reload**:
   - Add reloadConfiguration(Map<String, Object>) method to HandlerLifecycle
   - Default implementation is no-op
   - Handlers can override to support hot-reload without restart

4. **Metrics**:
   - Add getMetrics(): Map<String, Object> method to HandlerLifecycle
   - Handlers can expose metrics (request counts, latencies, errors)

### Why Abstract Class Pattern Supports Evolution

HandlerLifecycle is an abstract class, not an interface, to enable backward-compatible evolution. Future versions can add new methods with default implementations:

```java
// Future addition (backward compatible)
public abstract class HandlerLifecycle {
    // Existing methods (unchanged)
    public abstract void initialize(LifecycleContext context) throws Exception;
    public abstract EventHandler getHandler();
    public abstract void shutdown() throws Exception;
    
    // New methods with defaults (no breaking change)
    public HealthStatus healthCheck() {
        return HealthStatus.healthy();
    }
    
    public void reloadConfiguration(Map<String, Object> newConfig) {
        // Default: no-op (handler doesn't support reload)
    }
}
```

Existing handlers continue working without modification. New handlers can override new methods if they want advanced capabilities.

---

## Cross-References

- **Top-level domain specification:** /docs/domain_spec.md (bounded context map, general EventBob concepts)
- **Implementation examples:** /examples/echo-handler, /examples/lower-handler, /examples/upper-handler
- **Infrastructure adapters:** io.eventbob.spring module (Spring Boot integration)
