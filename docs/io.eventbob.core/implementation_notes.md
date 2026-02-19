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
- Requires no-args constructor (no dependency injection within handlers)
- Working directory dependency (JAR paths must be relative to cwd or absolute)
- No hot-reloading (handlers loaded once at bootstrap)


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
