# io.eventbob.core Implementation Notes

## Module-Specific Patterns

### Immutable Value Objects

**Applied in:** Event

**Pattern:**
- Private constructor taking Builder
- All fields `private final`
- Defensive copying for collections
- `toBuilder()` for copy-on-write transformations
- Static factory `builder()` for construction

**Type flexibility:**
- Event uses `Object` types for parameters, metadata, and payload fields
- Reflects JSON-compatible values without artificial `Serializable` constraint
- Honesty over false type safety: any Java object accepted, serialization concerns handled at boundaries

### Composable Handlers

**Applied in:** EventHandler, EventBob

**Pattern:**
- Single interface: `Event handle(Event event, Dispatcher dispatcher)`
- EventBob routes events to target-specific handlers
- Handlers registered via `EventBob.Builder.handler(String target, EventHandler handler)`
- No handler subclasses - pure composition

### Handler Registration Pattern

**Applied in:** EventBob.Builder

**Pattern:**
```java
EventBob router = EventBob.builder()
    .handler("echo", new EchoHandler())
    .handler("lowercase", new LowercaseHandler())
    .build();
```

- Target string identifies the handler (stored in Event.target field)
- Handlers are immutably registered at build time
- Map is defensively copied and made unmodifiable
- HandlerNotFoundException thrown if target not found

### Async Execution Model

**Applied in:** EventBob, Dispatcher

**Pattern:**
- All event processing returns `CompletableFuture<Event>`
- EventBob uses virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Handler invocation happens asynchronously on virtual thread pool
- Error handling via `exceptionally()` with BiFunction error callback
- Default error event created if callback returns null

**Key characteristics:**
- Non-blocking: Callers receive CompletableFuture immediately
- Virtual threads: High concurrency without thread pool tuning
- Error recovery: Errors converted to error events, not propagated as exceptions

### Timeout Convenience Method

**Applied in:** Dispatcher (default interface method)

**Pattern:**
```java
default Event send(Event event, BiFunction<Throwable, Event, Event> onError, long timeoutMillis)
```

Synchronous wrapper over async `send()` for request-response patterns. Blocks until completion or timeout.

**Exception handling strategy:**
- `InterruptedException` - Restores interrupt flag via `Thread.currentThread().interrupt()` before wrapping. Preserves thread interruption semantics.
- `ExecutionException` - Unwraps and passes cause to EventHandlingException. Avoids double-wrapping exceptions.
- `TimeoutException` - Wraps with timeout-specific message.

**Design rationale:**
- Default method pattern: Only async variant needs implementation, sync variant provided automatically
- Explicit exception types: No catch-all `Exception` block. Each failure mode handled distinctly
- Interrupt flag restoration: Critical for correct behavior in interrupt-sensitive contexts (thread pools, executors)

## Testing Conventions (This Module)

**Test structure:**
```java
@Test
@DisplayName("Given X, when Y, then Z")
void testMethodName() {
  // Arrange, Act, Assert
}
```

**Examples:**
- EventTest - tests Event immutability, builder, defensive copying
- EventBobTest - tests routing by target, error cases
- HandlerNotFoundExceptionTest - tests exception when handler not found
- CapabilityTest - tests capability annotations
- DefaultErrorEventTest - tests error event creation

**Stub pattern:**
```java
class RecordingHandler implements EventHandler {
  Event received;
  Event toReturn;

  @Override
  public Event handle(Event event, Dispatcher dispatcher) {
    this.received = event;
    return toReturn != null ? toReturn : event;
  }
}
```

Simple stubs over mocks - verify behavior via recorded state.

## Code Quality

**Current status:** Clean baseline
- Zero external dependencies
- All classes have single responsibility
- Immutability enforced
- Defensive copying prevents external mutation
- Exception hierarchy is specific (HandlerNotFoundException vs UnexpectedEventHandlingException)

**No technical debt in this module.**

## JAR Loading Implementation

### HandlerLoader Interface

**Applied in:** HandlerLoader, JarHandlerLoader

**Pattern:**
- Public interface with constructor injection pattern
- Implementations receive dependencies (JAR paths, remote endpoints, etc.) via constructor
- Single method: `Map<String, EventHandler> loadHandlers()` (no parameters)
- Factory method: `HandlerLoader.jarLoader(Collection<Path>)` returns JAR-based implementation with injected paths
- Returns map of capability names to instantiated handlers
- Throws `IOException` for unreadable JARs, `IllegalStateException` for duplicate capabilities or instantiation failures

**Design rationale:**
- Constructor injection allows different loader implementations (JAR-based, remote, service registry, etc.)
- Parameterless `loadHandlers()` follows Command pattern (dependencies captured at construction time)
- Decouples loading strategy from usage (callers don't need to know source of handlers)

### JarHandlerLoader Implementation

**Location:** `io.eventbob.core` package (package-private visibility)

**URLClassLoader Strategy:**
- Creates one URLClassLoader per JAR file
- Parent class loader: `EventHandler.class.getClassLoader()` (ensures core types are shared)
- Each JAR is isolated from other JARs (prevents dependency version conflicts)
- URLClassLoader is closed via try-with-resources (resource management)

**Discovery Phase:**
- Scans each JAR using `JarFile.stream()` to iterate entries
- Filters for `.class` files (excludes directories)
- Converts entry names to fully qualified class names: `com/example/Handler.class` → `com.example.Handler`
- Loads each class using the JAR's URLClassLoader
- Checks if class implements `EventHandler` and is annotated with `@Capability`
- Extracts capability name from annotation
- Detects duplicate capability names across all JARs (fails fast)
- Returns `List<DiscoveredHandler>` (internal data structure)

**Instantiation Phase:**
- Iterates discovered handlers
- Instantiates each via reflection: `handlerClass.getDeclaredConstructor().newInstance()`
- Requires no-args constructor (fails if missing)
- Returns `Map<String, EventHandler>` ready for registration

**Error Handling:**
- Missing JAR files: throws `IOException` immediately (fail fast)
- Malformed JAR files: catches `ZipException`, logs warning, skips JAR (resilient)
- Class loading failures: logs at FINE level, skips class (expected for non-handler classes)
- Instantiation failures: throws `IllegalStateException` with cause (fail fast)
- Duplicate capabilities: throws `IllegalStateException` immediately (fail fast)

**DiscoveredHandler Record:**
- Internal data structure (package-private)
- Fields: `String capability`, `Class<? extends EventHandler> handlerClass`
- Compact constructor validates non-null, non-blank capability
- Used to separate discovery phase from instantiation phase

**Design Decisions:**
- Package-private visibility: Implementation detail, not part of public API
- Factory method pattern: `HandlerLoader.jarLoader(Collection<Path>)` decouples interface from concrete class
- Constructor injection: JAR paths provided at construction, used by parameterless `loadHandlers()`
- Two-phase design: Discovery separated from instantiation (easier to test, clearer logic flow)
- Reflection vs ServiceLoader: Reflection chosen for simplicity and explicit control over class loading

**Known Limitations:**

**JarHandlerLoader (POJO strategy):**
- Requires no-args constructor (no dependency injection within handlers)
- No lifecycle management (no initialization or cleanup hooks)
- Working directory dependency (JAR paths must be relative to cwd or absolute)
- No hot-reloading (handlers loaded once at bootstrap)

**LifecycleHandlerLoader (lifecycle strategy):**
- Configuration loading from application.yml not yet implemented (returns empty map - YAML parsing is TODO)
- Lifecycle class itself requires no-args constructor (handler can use constructor DI)
- Working directory dependency (JAR paths must be relative to cwd or absolute)
- No hot-reloading (handlers loaded once at bootstrap)

## Lifecycle-Based Handler Loading

### HandlerLifecycle Contract

**Applied in:** HandlerLifecycle (abstract class), LifecycleContext (interface)

**Pattern:**
EventBob is a container for embedded microservices. Like other containers (Servlet, Spring, Dropwizard), it defines a lifecycle contract that handlers implement to integrate with the container.

**HandlerLifecycle abstract class:**
```java
public abstract class HandlerLifecycle {
    public abstract void initialize(LifecycleContext context) throws Exception;
    public abstract EventHandler getHandler();
    public abstract void shutdown() throws Exception;
}
```

**LifecycleContext interface:**
```java
public interface LifecycleContext {
    Map<String, Object> getConfiguration();
    Dispatcher getDispatcher();
    <T> Optional<T> getFrameworkContext(Class<T> type);
}
```

**Design rationale:**
- Abstract class (not interface) allows future evolution without breaking existing implementations
- Future versions can add methods (health checks, metrics, config reload) with default implementations
- Maintains backward compatibility as EventBob evolves

### LifecycleHandlerLoader Implementation

**Location:** `io.eventbob.core` package (package-private visibility)

**Factory method:**
```java
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher)
HandlerLoader.lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher, Object frameworkContext)
```

**JAR Structure Convention:**
```
handler.jar
├── com/example/MyHandlerLifecycle.class
├── com/example/MyHandler.class (implements EventHandler)
├── META-INF/
│   └── eventbob-handler.properties (declares lifecycle.class)
└── application.yml (handler-specific configuration)
```

**eventbob-handler.properties format:**
```properties
lifecycle.class=com.example.MyHandlerLifecycle
```

### Lifecycle Initialization Sequence

**Eight-step loading process:**

1. **Load JAR into isolated URLClassLoader**
   - One URLClassLoader per JAR
   - Parent: `HandlerLifecycle.class.getClassLoader()` (ensures core types are shared)
   - Tracked in `List<URLClassLoader>` for cleanup

2. **Read META-INF/eventbob-handler.properties**
   - Properties file declares `lifecycle.class` property
   - If missing: JAR is skipped (logged as warning, graceful degradation)

3. **Load configuration from application.yml**
   - Currently returns empty map (TODO: implement YAML parsing using SnakeYAML)
   - Handlers must not depend on configuration until parsing is implemented

4. **Instantiate lifecycle class**
   - Via reflection: `lifecycleClass.getDeclaredConstructor().newInstance()`
   - Requires no-args constructor on lifecycle class
   - Validates lifecycle class extends `HandlerLifecycle`

5. **Call lifecycle.initialize(context)**
   - Provides `LifecycleContext` with configuration, dispatcher, and optional framework context
   - Lifecycle implementation wires handler with dependencies (Spring context, DB connections, etc.)
   - Initialization failures throw `IllegalStateException` (fail-fast)

6. **Call lifecycle.getHandler()**
   - Retrieves initialized `EventHandler` instance
   - Null handler throws `IllegalStateException`

7. **Extract capabilities from handler**
   - Uses `handlerClass.getAnnotationsByType(Capability.class)` to find all `@Capability` annotations
   - Supports multi-capability handlers (same instance registered for multiple capability names)
   - Duplicate capabilities throw `IllegalStateException`

8. **Track lifecycle for shutdown**
   - Lifecycle added to `List<HandlerLifecycle>` for cleanup
   - When `close()` is called, all lifecycles are shut down before class loaders are closed

### Resource Management

**Shutdown sequence (via close()):**
1. Call `shutdown()` on all tracked lifecycles (handlers can reference classes during shutdown)
2. Close all URLClassLoaders (releases JAR file handles and memory)
3. First exception encountered is thrown (subsequent failures logged as warnings)

**Design rationale:**
- Lifecycles shut down before class loaders close (handlers may reference classes during cleanup)
- Class loader closure is deferred to allow Spring context shutdown, DB connection cleanup, etc.
- Resource cleanup is thorough: both handler resources and class loader resources released

### Framework Integration

**Framework-agnostic design:**
- Core EventBob has zero framework dependencies
- `LifecycleContext.getFrameworkContext<T>()` provides optional framework access
- Handlers can optionally use microlith's framework context (Spring ApplicationContext, Dropwizard Environment, etc.)
- If framework context is unavailable, handlers create standalone contexts

**Example patterns:**

**Spring handler using framework context:**
```java
public void initialize(LifecycleContext context) {
    Optional<ApplicationContext> springContext =
        context.getFrameworkContext(ApplicationContext.class);

    if (springContext.isPresent()) {
        // Use parent Spring context
        this.handler = springContext.get().getBean(MyHandler.class);
    } else {
        // Create own Spring context
        this.handler = createStandaloneSpringHandler(context);
    }
}
```

**Manual wiring (no framework):**
```java
public void initialize(LifecycleContext context) {
    String dbUrl = (String) context.getConfiguration().get("database.url");
    DataSource ds = createDataSource(dbUrl);
    this.handler = new MyHandler(ds, context.getDispatcher());
}
```

### Dependency Injection Support

**Lifecycle class:**
- Requires no-args constructor (instantiated via reflection)
- Wires dependencies in `initialize()` method

**Handler class:**
- Can use constructor injection (lifecycle wires dependencies)
- Example: `new MyHandler(dataSource, httpClient, context.getDispatcher())`

**Contrast with JarHandlerLoader:**
- JarHandlerLoader: Handler class needs no-args constructor, no DI
- LifecycleHandlerLoader: Lifecycle class needs no-args constructor, handler can use DI

### Error Handling

**Graceful degradation:**
- Missing JAR file: Logged as warning, other JARs continue loading
- Missing eventbob-handler.properties: JAR skipped (logged as warning)
- Malformed properties file: JAR skipped (logged as warning)
- Invalid lifecycle class name: JAR skipped (logged as warning)

**Fail-fast:**
- Lifecycle initialization failure: Throws `IllegalStateException`
- Null handler from `getHandler()`: Throws `IllegalStateException`
- Handler with no `@Capability` annotations: Throws `IllegalStateException`
- Duplicate capability names: Throws `IllegalStateException`

**Design rationale:**
- Graceful degradation for discovery phase (one bad JAR should not prevent loading others)
- Fail-fast for contract violations (null handler, missing capabilities are programming errors)

### Test Coverage

**LifecycleHandlerLoaderTest:** 17 tests covering:
- Missing JAR handling (graceful degradation)
- Empty JAR list handling
- Non-readable file handling
- Missing properties file handling (graceful degradation)
- Invalid lifecycle class name handling (graceful degradation)
- Lifecycle initialization failure scenarios
- Null handler from lifecycle detection
- Handler with no capabilities detection
- Duplicate capability detection (fail-fast)
- Successful lifecycle loading with valid JAR
- Multi-capability handler support
- Resource cleanup (shutdown sequence verification)

**Test strategy:**
- Uses `@TempDir` for isolated JAR creation in tests
- Stub `Dispatcher` for lifecycle initialization (sufficient for loading tests)
- Tests both success paths and all error conditions
- Verifies graceful degradation vs fail-fast behavior matches specification

## Multi-Capability Handler Support

### Pattern Overview

EventBob supports handlers that declare multiple capabilities. A single handler class can be annotated with multiple `@Capability` annotations (using Java's repeatable annotation mechanism) or an explicit `@Capabilities` container annotation.

### Discovery and Registration

**Uniform extraction via `getAnnotationsByType`:**
- `JarHandlerLoader` uses `handlerClass.getAnnotationsByType(Capability.class)` to extract all capabilities
- This method handles both single `@Capability` and multiple `@Capability` annotations uniformly
- Also recognizes explicit `@Capabilities` container annotation
- Returns all `@Capability` annotations regardless of declaration style

**Discovery phase behavior:**
- For each discovered handler class, all capability names are extracted
- Each capability generates a `DiscoveredHandler(capability, handlerClass)` record
- A multi-capability handler produces multiple `DiscoveredHandler` records with the same `handlerClass`
- Duplicate capability names across different handlers throw `IllegalStateException`

### Instance Sharing

**Single instance per handler class:**
```java
Map<Class<? extends EventHandler>, EventHandler> instanceCache = new HashMap<>();

for (DiscoveredHandler discovered : discoveredHandlers) {
    EventHandler handler = instanceCache.computeIfAbsent(
        discovered.handlerClass(), this::instantiateHandler);
    handlers.put(discovered.capability(), handler);
}
```

**Result:** The returned map contains multiple capability names pointing to the same handler instance:
```java
{
  "to-upper": caseHandlerInstance,
  "to-lower": caseHandlerInstance  // same object reference
}
```

### Domain Implications

**Shared state:** Since one instance serves all capabilities, any instance state is shared across all capability invocations. Handlers must be thread-safe (already required by EventBob contract).

**Capability discrimination:** The handler receives `Event.getTarget()` with the invoked capability name, allowing it to branch behavior based on which capability was called.

**Example pattern:**
```java
@Capability("to-upper")
@Capability("to-lower")
public class CaseHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) {
        String input = (String) event.getPayload();
        String result = "to-upper".equals(event.getTarget())
            ? input.toUpperCase()
            : input.toLowerCase();
        return event.toBuilder()
            .payload(result)
            .build();
    }
}
```

### Uniqueness Enforcement

**Domain invariant preserved:** Each capability identifier must be unique within the microlith. Enforcement happens during discovery:
- Capability names are tracked in `Set<String> seenCapabilities`
- Duplicate capability name throws `IllegalStateException` immediately
- This prevents both:
  - Two different handlers declaring the same capability
  - Single handler declaring the same capability twice (repeatable annotation with duplicate values)

## Performance Characteristics

**Router:** O(1) map lookup by target string
**Event construction:** Defensive copy of metadata/parameters maps
**Memory:** Each Event allocates immutable copies of collections
**JAR loading:** One-time bootstrap cost (not performance-critical)
**Class loading:** Per-JAR URLClassLoader (acceptable overhead for isolation)

**Not optimized for high throughput yet.** Baseline implementation prioritizes correctness and clarity.
