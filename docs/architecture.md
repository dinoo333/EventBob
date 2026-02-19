# EventBob Architecture

## System Purpose

EventBob bundles multiple microservices into a microlith to solve the chatty-application anti-pattern. When too many microservices cause network saturation and poor performance, EventBob loads their JARs into a single server for in-process communication.

**Location Transparency Extension:** EventBob also supports remote handler loading via HTTP, enabling hybrid architectures where some handlers run in-process (from JARs) and others run remotely (via HTTP endpoints). The routing layer treats both types identically — clients see a unified capability namespace regardless of handler location.

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
│   - Remote handler loading adapters  │
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
│   - Concrete remote endpoint config  │
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

Core defines HandlerLoader interface with factory methods:

```java
public interface HandlerLoader {
    /**
     * Load handlers from the configured source.
     * 
     * @return Map of capability name to EventHandler
     * @throws IOException if loading fails
     */
    Map<String, EventHandler> loadHandlers() throws IOException;
    
    /**
     * Factory method: Create a JAR-based handler loader.
     * 
     * @param jarPaths Collection of JAR file paths to load handlers from
     * @return HandlerLoader that loads from the specified JARs
     */
    static HandlerLoader jarLoader(Collection<Path> jarPaths) {
        return new JarHandlerLoader(jarPaths);  // package-private implementation
    }
    
    /**
     * Factory method: Create a remote HTTP-based handler loader.
     * Implementation provided by infrastructure layer (io.eventbob.spring).
     * 
     * @param remoteEndpoint URL of remote EventBob server
     * @param capabilities Set of capability names available at remote endpoint
     * @return HandlerLoader that proxies to remote handlers via HTTP
     */
    static HandlerLoader remoteLoader(String remoteEndpoint, Set<String> capabilities) {
        // Implementation discovered via ServiceLoader mechanism
        // This allows core to define the contract without depending on HTTP infrastructure
        throw new UnsupportedOperationException("Remote loader requires io.eventbob.spring on classpath");
    }
}
```

Spring infrastructure depends only on the interface:

```java
@Configuration
public class EventBobConfig {
    private final List<Path> handlerJarPaths;
    private final List<RemoteCapability> remoteCapabilities;
    
    public EventBobConfig(
            List<Path> handlerJarPaths,
            @Autowired(required = false) List<RemoteCapability> remoteCapabilities) {
        this.handlerJarPaths = handlerJarPaths;
        this.remoteCapabilities = remoteCapabilities != null ? remoteCapabilities : List.of();
    }
    
    private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader(handlerJarPaths);  // Factory method
        return loader.loadHandlers();
    }
    
    private Map<String, EventHandler> loadRemoteHandlers(HttpClient httpClient) throws IOException {
        HandlerLoader remoteLoader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
        return remoteLoader.loadHandlers();
    }
}
```

**Key insight:** Spring never sees JarHandlerLoader (package-private). It depends only on the public HandlerLoader interface. This is Dependency Inversion Principle in practice.

**Remote loading insight:** The `remoteLoader()` factory method is defined in core but implemented in infrastructure layer via ServiceLoader pattern. This preserves dependency direction (Spring → Core) while allowing infrastructure-specific implementations.

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
├── EventDto                       (HTTP/Event boundary DTO - anti-corruption layer)
├── adapter/
│   └── http/
│       ├── HttpEventHandlerAdapter (EventHandler → HTTP adapter)
│       └── RemoteEventDto          (Remote HTTP DTO - separate from local EventDto)
├── loader/
│   └── RemoteHandlerLoader        (HandlerLoader implementation for remote endpoints)
└── handlers/
    └── HealthcheckHandler         (Built-in system handler)
```

**Package structure rationale:**
- **Top-level:** Configuration and local REST endpoint (EventBobConfig, EventController, EventDto)
- **adapter/http/:** HTTP client-side adapters for remote handler invocation
- **loader/:** HandlerLoader implementations beyond core's JarHandlerLoader
- **handlers/:** Built-in system handlers

**No main class.** This is a library module. Applications import and configure it.

### Microlith Applications (Example: Echo with Spring)

```
io.eventbob.example.microlith.spring.echo
└── EchoApplication                 (Spring Boot main class + JAR path config)
```

More microlith applications will follow this pattern (e.g., `io.eventbob.example.microlith.spring.upper` for testing remote invocation, or `io.eventbob.example.microlith.dropwizard.echo` for alternative framework).

---

## Component Diagram

### Local Handler Loading (Original)

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

### Remote Handler Loading (New)

```
┌───────────────────────────────┐        ┌───────────────────────────────┐
│  Microlith A                  │        │  Microlith B                  │
│                               │        │                               │
│  ┌─────────────────────┐     │        │  ┌─────────────────────┐     │
│  │  EventBob Router    │     │  HTTP  │  │  EventBob Server    │     │
│  │                     │ ────┼───────>│  │                     │     │
│  │  [RemoteLoader]     │     │        │  │  [upper.jar]        │     │
│  │  capability: upper  │     │        │  │                     │     │
│  │                     │     │        │  │  POST /events       │     │
│  └─────────────────────┘     │        │  └─────────────────────┘     │
│                               │        │                               │
└───────────────────────────────┘        └───────────────────────────────┘

Microlith A sees "upper" capability in its namespace.
At runtime, "upper" events are proxied to Microlith B via HTTP.
Domain code in Microlith A is unaware of the location — it just calls EventHandler.
```

**Location transparency:** The EventBob router in Microlith A treats remote handlers identically to local handlers. Both implement EventHandler interface. Clients cannot tell the difference.

---

## Dependency Rules

**✅ Allowed:**
- `io.eventbob.example.microlith.*` → `io.eventbob.spring`
- `io.eventbob.spring` → `io.eventbob.core`
- `io.eventbob.spring.adapter.*` → `io.eventbob.core` (adapters depend on domain)
- `io.eventbob.spring.loader.*` → `io.eventbob.core` (loaders implement HandlerLoader)
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
- **NO HTTP client dependencies** (remote loading is infrastructure concern)

### Spring Library (`io.eventbob.spring`)
- Generic EventBob configuration (constructor injection)
- HTTP transport adapters (server-side: EventController, client-side: HttpEventHandlerAdapter)
- Remote handler loading (RemoteHandlerLoader, RemoteEventDto)
- REST endpoints
- Framework-specific code (Spring Boot, Spring Web)
- **NO main class, NO hard-coded paths**

### Microlith Applications (`io.eventbob.example.microlith.*`)
- Spring Boot main class
- Concrete JAR path configuration
- Concrete remote endpoint configuration
- Deployment-specific wiring
- Runtime dependencies on handler JARs

### Rule
Core defines WHAT (domain model + abstractions). Infrastructure library defines HOW (with specific frameworks). Applications define WHICH (which handlers to load, where to deploy).

---

## Location Transparency Architecture

### Design Goal

Domain code should be agnostic to handler location. Whether a handler runs in the same JVM (loaded from JAR) or in a remote process (accessed via HTTP) is a deployment decision, not a domain concern.

### How It Works

1. **Unified Interface:** Both local and remote handlers implement the same EventHandler interface from core
2. **Adapter Pattern:** HttpEventHandlerAdapter wraps HTTP client logic and exposes EventHandler interface
3. **Transparent Routing:** EventBob router treats all handlers identically — it calls `handle(Event)` regardless of implementation
4. **Anti-Corruption Layer:** EventDto and RemoteEventDto prevent HTTP types from leaking into domain

### Remote Handler Loading Sequence

```
Application configures remote endpoint
        ↓
RemoteHandlerLoader creates HttpEventHandlerAdapter
        ↓
HttpEventHandlerAdapter registered with EventBob router
        ↓
Client code calls eventBob.processEvent(event)
        ↓
EventBob routes to HttpEventHandlerAdapter (transparent)
        ↓
HttpEventHandlerAdapter converts Event → RemoteEventDto → HTTP POST
        ↓
Remote server responds with RemoteEventDto → Event
        ↓
Client receives Event (indistinguishable from local handler)
```

**Key insight:** The adapter lives in infrastructure layer. Core remains pure. Remote complexity is encapsulated.

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
   (+ remote upper) (local only)    (+ remote archive)
```

Each microlith:
- Imports io.eventbob.spring library
- Provides @Bean for List<Path> handlerJarPaths
- Optionally provides @Bean for List<RemoteCapability> remoteCapabilities
- Has its own Spring Boot main class
- Loads different combinations of handlers (local and/or remote)

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
- Uses HandlerLoader.jarLoader(Collection<Path>) factory method
- May provide HandlerLoader.remoteLoader() implementation

Core remains unchanged regardless of infrastructure choice.

---

## Handler Lifecycle

### The Problem

The original JAR loading mechanism (`HandlerLoader.jarLoader()`) works for simple POJO handlers with no-arg constructors. But real microservices need:
- Configuration (database URLs, API keys)
- Dependencies (DataSource, HTTP clients, repositories)
- Framework integration (Spring contexts, Dropwizard environments)
- Startup and shutdown hooks (resource management)

POJOs with no-arg constructors cannot solve this. We need a lifecycle contract.

### The Solution: HandlerLifecycle Contract

EventBob is a container for embedded microservices. Like other containers (Servlet, Spring, Dropwizard), it defines a lifecycle contract that handlers implement to integrate with the container.

**HandlerLifecycle is a domain contract.** It lives in core (`io.eventbob.core`) alongside Event and EventHandler. It is not infrastructure. It defines WHAT the lifecycle is, not HOW any particular framework implements it.

Think of it like `javax.servlet.Servlet`. The servlet-api JAR defines the contract. Tomcat, Jetty, and Undertow provide different implementations. The contract itself is framework-agnostic.

### Two Handler Loading Strategies

HandlerLoader now provides two factory methods:

```java
// Strategy 1: POJO handlers (original)
HandlerLoader.jarLoader(Collection<Path> jarPaths)

// Strategy 2: Lifecycle handlers (new)
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher)
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher, Object frameworkContext)
```

**When to use jarLoader:**
- Simple handlers with no dependencies
- No configuration needed
- No startup/shutdown requirements
- No framework integration

**When to use lifecycleLoader:**
- Handlers need configuration (from application.yml)
- Handlers depend on services, repositories, or HTTP clients
- Handlers integrate with Spring, Dropwizard, or other frameworks
- Handlers manage resources that require cleanup

Both strategies produce the same result: `Map<String, EventHandler>`. The router does not care which strategy loaded the handler.

### Handler Initialization Sequence

For lifecycle handlers, initialization follows this sequence:

```
1. JAR loaded into isolated URLClassLoader
       ↓
2. Read META-INF/eventbob-handler.properties
   - Find lifecycle.class property
       ↓
3. Load application.yml configuration (TODO: not yet implemented)
   - Parsed into Map<String, Object>
   - Provided to lifecycle via LifecycleContext
       ↓
4. Instantiate lifecycle class (no-arg constructor)
       ↓
5. Call lifecycle.initialize(LifecycleContext)
   - Lifecycle receives: configuration, dispatcher, optional framework context
   - Lifecycle creates services, connects to databases, initializes Spring contexts, etc.
   - Lifecycle wires dependencies and constructs the handler
       ↓
6. Call lifecycle.getHandler()
   - Returns fully-initialized EventHandler
       ↓
7. Extract @Capability annotations from handler
   - Register handler with EventBob router
       ↓
8. Track lifecycle for shutdown
   - When EventBob shuts down, lifecycle.shutdown() is called
   - Then URLClassLoader is closed
```

**Ordering matters:** Class loaders are closed AFTER lifecycle shutdown. This allows handlers to reference classes during cleanup (closing Spring contexts, database connections, etc.).

### Framework-Agnostic Context Mechanism

The LifecycleContext provides three things:

1. **Configuration:** `Map<String, Object> getConfiguration()`
   - Loaded from handler JAR's application.yml (not yet implemented)
   - Handler can parse it using Jackson, SnakeYAML, Spring's @ConfigurationProperties, etc.

2. **Dispatcher:** `Dispatcher getDispatcher()`
   - Used to invoke other capabilities during event processing

3. **Framework Context:** `<T> Optional<T> getFrameworkContext(Class<T> type)`
   - Generic access to framework-specific resources
   - Example: `context.getFrameworkContext(ApplicationContext.class)` for Spring
   - Returns empty if microlith doesn't use that framework
   - Core has no dependency on Spring, Dropwizard, or any framework
   - The generic type parameter preserves type safety while maintaining framework-agnosticism

This is Dependency Inversion. The core defines the abstraction (`LifecycleContext`). The infrastructure layer (Spring microlith) provides the framework context. The handler (in an isolated JAR) optionally uses it.

### Dependency Injection Pattern

The lifecycle implementation demonstrates a three-layer dependency injection pattern:

```
LifecycleHandlerLifecycle (example JAR)
    ↓ creates
EchoService (example JAR)
    ↓ wired to
EchoHandler (example JAR)
```

**Example from echo handler:**

```java
public class EchoHandlerLifecycle extends HandlerLifecycle {
    @Override
    public void initialize(LifecycleContext context) {
        // Create service with dispatcher from context
        EchoService echoService = new EchoService(context.getDispatcher());
        // Wire service to handler via constructor injection
        this.handler = new EchoHandler(echoService);
    }
}
```

The handler depends on the service. The service depends on the dispatcher. The lifecycle wires everything together. This is manual dependency injection -- no framework required, but frameworks can be used if needed.

### Resource Management

HandlerLoader extends `AutoCloseable`. When EventBob shuts down, it calls `loader.close()`, which:

1. Calls `lifecycle.shutdown()` on all tracked lifecycles
   - Handlers close database connections, shutdown thread pools, close Spring contexts, etc.
2. Closes all URLClassLoaders
   - Releases class loader resources

This ensures clean shutdown. Resources are released in the correct order (lifecycles first, then class loaders).

### Current Limitations

**Configuration loading is not yet implemented.** The `LifecycleContext.getConfiguration()` method currently returns an empty map. YAML parsing using SnakeYAML or similar will be added in a future commit.

Handlers must not depend on configuration until this is implemented. Use hard-coded defaults or environment variables as temporary workarounds.

### What Belongs Where (Updated)

**Core (`io.eventbob.core`):**
- Domain contracts: Event, EventHandler, Capability, **HandlerLifecycle, LifecycleContext**
- Routing abstractions: Dispatcher
- Loading abstractions and implementations: HandlerLoader, JarHandlerLoader, **LifecycleHandlerLoader**
- NO framework dependencies (Spring, Dropwizard, etc.)
- NO HTTP client dependencies (remote loading is infrastructure concern)

**Spring Library (`io.eventbob.spring`):**
- Generic EventBob configuration (constructor injection)
- HTTP transport adapters (server-side, client-side)
- Remote handler loading
- REST endpoints
- Framework-specific code (Spring Boot, Spring Web)
- NO main class, NO hard-coded paths
- **Can provide framework context via LifecycleContext.getFrameworkContext(ApplicationContext.class)**

**Example Handlers (`io.eventbob.example.echo`, etc.):**
- EventHandler implementations
- **HandlerLifecycle implementations**
- **Service layer (business logic extracted from handlers)**
- META-INF/eventbob-handler.properties (declares lifecycle.class)
- application.yml (handler configuration, not yet loaded)
- NO dependencies on microliths (examples are isolated JARs)

**Microlith Applications (`io.eventbob.example.microlith.*`):**
- Spring Boot main class
- Concrete JAR path configuration
- Concrete remote endpoint configuration
- Deployment-specific wiring
- Runtime dependencies on handler JARs

### Rule (Updated)

Core defines WHAT (domain model + abstractions + lifecycle contract). Infrastructure library defines HOW (with specific frameworks). Applications define WHICH (which handlers to load, where to deploy). **Handler JARs define HOW THEY WIRE THEMSELVES (via lifecycle implementations).**

