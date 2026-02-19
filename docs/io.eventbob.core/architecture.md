# io.eventbob.core Architecture

## Module Purpose

The core domain module defining event routing abstractions, JAR handler loading, and primitives. This is the innermost layer - all other modules depend on core, core depends on nothing (except JDK).

---

## Current Implementation Status

**Implemented:**
- Event data structure (immutable value object)
- EventHandler interface (universal contract)
- EventBob (string-based routing, implements AutoCloseable)
- Dispatcher interface (event dispatch contract)
- HandlerLoader interface (JAR loading abstraction)
- JarHandlerLoader implementation (URLClassLoader-based loading)
- DiscoveredHandler record (internal discovery structure)
- DefaultErrorEvent factory (internal error handling)
- Exception hierarchy
- Capability annotation and Capabilities vocabulary

**Not Yet Implemented:**
- Hierarchical routing (wildcards, patterns)
- Route registry abstraction
- Adapters (separate modules)

---

## Package Structure (Flat)

All classes live in `io.eventbob.core` - no subpackages. This flat structure reflects the cohesive nature of the domain.

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

**Why flat?** Core is small and operates at a single abstraction level. All classes are domain primitives, routing logic, or handler loading. Subpackages would create artificial boundaries without clarity gains.

---

## Key Abstractions

### EventHandler Interface

The universal contract for event processing:

```java
public interface EventHandler {
  Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException;
}
```

**Design:** Synchronous request-response. Single method. Composable. Handlers receive a Dispatcher to enable event delegation during processing.

### Event Structure

Immutable value object with builder:
- `source` - Producer identifier
- `target` - Consumer identifier
- `metadata` - Routing and observability metadata
- `parameters` - Business data
- `payload` - Request body

**Key invariant:** Metadata carries infrastructure concerns (correlation-id, method, path). Parameters carry business data.

### EventBob

Routes events by exact target match to registered handlers. Implements AutoCloseable for resource cleanup (shuts down background executor).

**Current limitation:** No hierarchical routing, no wildcards. Flat string lookup.

**Design:** EventBob does NOT implement EventHandler. It exposes `processEvent()` which returns `CompletableFuture<Event>` for async processing. Handlers are executed on virtual threads.

### Dispatcher

Contract for sending events from within handlers. Provides two send methods:

**Async variant:**
```java
CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError)
```
Returns immediately with a future. Caller controls timeout via `future.get(timeout, unit)`.

**Sync variant (default method):**
```java
Event send(Event event, BiFunction<Throwable, Event, Event> onError, long timeoutMillis)
```
Blocks until response or timeout. Convenience method for synchronous request-response. Handles three exception types explicitly:
- `InterruptedException` - restores interrupt flag, wraps in EventHandlingException
- `ExecutionException` - unwraps cause, wraps in EventHandlingException
- `TimeoutException` - wraps in EventHandlingException

**Design decision:** Sync method is a default interface method. Implementations only provide async variant. This keeps Dispatcher interface minimal while supporting both patterns.

### Capabilities Vocabulary

Constants defining standard capability names and routing metadata keys:
- Routing: CORRELATION_ID, REPLY_TO, METHOD, PATH
- Observability: TRACE_ID, SPAN_ID

**Design decision:** Core defines only universal keys. Transport-specific keys (http.*, grpc.*) belong in adapter modules.

---

## JAR Handler Loading

### HandlerLoader Interface (Public API)

The abstraction for loading handlers from external sources:

```java
public interface HandlerLoader {
    Map<String, EventHandler> loadHandlers(Collection<Path> jarPaths) throws IOException;
    
    static HandlerLoader jarLoader() {
        return new JarHandlerLoader();
    }
}
```

**Contract:**
- Input: Collection of JAR file paths
- Output: Map of capability names to instantiated EventHandler instances. When a handler class provides multiple capabilities, multiple map entries point to the same handler instance.
- Throws: IOException if JAR files cannot be read
- Throws: IllegalStateException if duplicate capabilities found or instantiation fails

**Factory method pattern:** The static `jarLoader()` method hides the implementation (JarHandlerLoader) from clients. Infrastructure code depends only on this interface.

### JarHandlerLoader Implementation (Package-Private)

URLClassLoader-based implementation that loads handlers from JAR files with isolation.

**ClassLoader strategy:**
- One URLClassLoader per JAR file
- Parent class loader: the loader that loaded EventHandler
- Result: Core classes shared, JAR dependencies isolated

**Discovery algorithm:**
1. For each JAR file:
   - Create isolated URLClassLoader
   - Scan all .class files
   - Load each class
   - Check if class implements EventHandler AND has @Capability or @Capabilities annotation
   - Extract capability names from annotation(s) using `getAnnotationsByType(Capability.class)`
   - For multi-capability handlers, create multiple (capability, class) pairs - one per capability name
   - Collect all (capability, class) pairs
2. Check for duplicate capabilities across all JARs (at capability-name level, not class level)
3. Instantiate all discovered handlers using no-args constructors (one instance per unique handler class)

**Error handling:**
- **Missing JAR:** IOException thrown immediately (fail fast)
- **Malformed JAR:** Warning logged, JAR skipped, continue with other JARs
- **Duplicate capabilities:** IllegalStateException thrown (fail fast)
- **Class loading failures:** Fine-level logging, class skipped, continue scanning
- **Instantiation failures:** IllegalStateException thrown (fail fast)

**Why this error strategy?**
- Missing/malformed JARs: Infrastructure misconfiguration, should be detected at startup
- Duplicate capabilities: Semantic conflict, cannot proceed safely
- Class loading failures: Expected (JARs may reference unavailable classes), gracefully skip
- Instantiation failures: Handler contract violation, cannot proceed

### DiscoveredHandler Record (Package-Private)

Internal data structure capturing discovery results:

```java
record DiscoveredHandler(
    String capability,
    Class<? extends EventHandler> handlerClass
) { }
```

**Purpose:** Separates discovery phase from instantiation phase. Discovery collects metadata and validates uniqueness. Instantiation happens only after all handlers are discovered and validated.

**Visibility:** Package-private. This is an internal structure used within JarHandlerLoader. Clients never see it.


### Multi-Capability Handler Support

Handlers may declare multiple capabilities using Java's repeatable annotation mechanism:

```java
@Capability("get-message")
@Capability("create-message")
@Capability("update-message")
public class MessageHandler implements EventHandler {
    // Single class, three capability registrations
}
```

**Instance sharing:** When a handler class declares multiple capabilities, JarHandlerLoader creates a single instance and registers it under each capability name. The instantiation phase maintains an instance cache (`Map<Class<?>, EventHandler>`) to ensure each handler class is instantiated exactly once, regardless of how many capabilities it declares.

**Map semantics:** The returned `Map<String, EventHandler>` contains multiple entries pointing to the same handler instance:
```
{
  "get-message" -> messageHandlerInstance,
  "create-message" -> messageHandlerInstance,
  "update-message" -> messageHandlerInstance
}
```

**Thread-safety requirement:** Handlers must be thread-safe. This was always required (EventBob routes concurrent requests to handlers), but the shared instance pattern makes it explicit. A single handler instance services all requests for all its declared capabilities.

**Duplicate detection:** Enforced at capability-name level, not class level. Two handler classes cannot declare the same capability name, but one handler class can declare multiple distinct capability names. If `MessageHandler` declares "get-message" and `UserHandler` also declares "get-message", instantiation fails with `IllegalStateException`.

**Boundary preservation:** This is entirely a package-private implementation detail within JarHandlerLoader. The public HandlerLoader interface contract (`Map<String, EventHandler> loadHandlers()`) remains unchanged. Infrastructure code cannot observe whether multiple map entries share the same instance - it sees only the capability-to-handler mapping.

### DefaultErrorEvent Factory (Package-Private)

Factory for creating error events when error handlers fail or return null:

```java
class DefaultErrorEvent {
  static Event create(Throwable error, Event originalEvent) { ... }
}
```

**Purpose:** Ensures EventBob always returns an Event, even when error handling fails. Prevents null returns from propagating.

**Visibility:** Package-private. Internal error handling mechanism. Clients never invoke this directly.

---

## Visibility Boundaries

### Public API (Infrastructure Depends On)
- Event, Event.Builder
- EventHandler
- EventBob, EventBob.Builder
- Dispatcher
- HandlerLoader (interface only)
- Capability (annotation)
- Capabilities (constants)
- EventHandlingException, HandlerNotFoundException

### Package-Private Internals (Hidden from Infrastructure)
- JarHandlerLoader (implementation hidden by factory method)
- DiscoveredHandler (internal structure)
- DefaultErrorEvent (internal error handling)

**Enforcement:** Java access modifiers. Infrastructure cannot import package-private classes.

**Rationale:** Public API is stable and minimal. Internal implementations can change without affecting infrastructure. This is Information Hiding - a foundational principle of modular design.

---

## Dependencies

**External dependencies:** ZERO runtime dependencies

**JDK classes used:**
- `java.util.*` (collections, maps)
- `java.nio.file.*` (Path for JAR location)
- `java.net.*` (URLClassLoader, URL)
- `java.util.jar.*` (JarFile, JarEntry)
- `java.lang.reflect.*` (Constructor, Class)
- `java.util.logging.*` (Logger)
- `java.util.concurrent.*` (CompletableFuture, ExecutorService)

**Why no dependencies?** Core is domain logic. Domain logic must not depend on frameworks, libraries, or infrastructure. This keeps the domain testable, portable, and stable.

**Dependents:** `io.eventbob.spring` depends on this module. Future adapter modules will also depend on this module.

---

## Testing Strategy

- Unit tests for each component
- Behavioral tests using @DisplayName("Given... when... then...")
- Simple stubs over mocks (e.g., RecordingHandler)
- No infrastructure dependencies in tests
- Tests verify contracts, not implementations

**Loader testing:**
- Test JAR isolation (handlers cannot see each other's classes)
- Test duplicate detection (duplicate capabilities throw IllegalStateException)
- Test error resilience (malformed JARs do not stop processing)
- Test instantiation (handlers are created correctly)

---

## Future Evolution

### When Adapters Are Added
- Adapters will implement EventHandler
- Adapters will use Capabilities vocabulary
- Core module remains unchanged

### When Routing Grows Complex
- May extract Router interface
- May add pattern-based routing (wildcards, regex)
- May add route registry abstraction
- Current EventBob remains for simple cases

### When Async Is Needed
- May add AsyncEventHandler interface
- May add EventPublisher for fire-and-forget
- Current EventHandler remains for sync cases

### When Loader Becomes Configurable
- May add LoaderConfig interface
- May add filtering strategies (include/exclude patterns)
- Current HandlerLoader remains as default

**Restraint principle:** Do not build these until they are needed. Extract patterns reactively, not proactively.

---

## Architectural Invariants (Must Never Violate)

1. **Zero external dependencies** - Core depends only on JDK
2. **Inward-only dependencies** - Core never imports from infrastructure
3. **Package-private implementations** - Only abstractions are public
4. **Immutable domain objects** - Event is immutable, builders create new instances
5. **Flat package structure** - All classes in `io.eventbob.core`, no subpackages

**Enforcement:** Maven module boundaries provide structural enforcement. Automated tests planned.
