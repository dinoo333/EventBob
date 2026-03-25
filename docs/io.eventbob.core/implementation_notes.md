# io.eventbob.core — Implementation Notes

### 1 Frameworks used

No external frameworks. Zero runtime dependencies. JUL (`java.util.logging`) is used for loader diagnostics — it is the only logging dependency, chosen to avoid requiring SLF4J on the classpath of handler JARs loaded in isolated class loaders.

### 2 Coding patterns

- **Immutable value object with copy builder**: `Event` uses a private constructor that accepts a `Builder`, copies all mutable fields defensively via `Collections.unmodifiableMap(new LinkedHashMap<>(...))`, and exposes `toBuilder()` for copy-on-write modification. `payload` is `Object` rather than a constrained type; serialisation concerns are deferred to infrastructure boundaries.

- **Virtual-thread executor**: `EventBob` creates `Executors.newVirtualThreadPerTaskExecutor()` at construction time. All handler invocations run on virtual threads, providing high concurrency without explicit pool tuning. Errors are caught via `CompletableFuture.exceptionally()` and converted to error events.

- **Blocking close with interrupt preservation**: `EventBob.close()` calls `shutdown()` then `awaitTermination(30, TimeUnit.SECONDS)`. `InterruptedException` restores the interrupt flag via `Thread.currentThread().interrupt()` before returning. This ensures caller interrupt semantics are not swallowed and that handler lifecycle teardown only starts after all in-flight threads have exited.

- **Default method as synchronous wrapper**: `Dispatcher.send(Event, BiFunction, long)` is a default interface method that wraps the async `send(Event, BiFunction)`. Each of the three checked exception types (`InterruptedException`, `ExecutionException`, `TimeoutException`) is handled distinctly: interrupt restores the flag, execution exception unwraps the cause to avoid double-wrapping, timeout carries a timeout-specific message. No catch-all `Exception` block is used.

- **Two-phase JAR loading (discovery then instantiation)**: `JarHandlerLoader` separates class scanning (producing `DiscoveredHandler` records) from reflection-based instantiation. The instance cache (`Map<Class, EventHandler>`) ensures a multi-capability handler class is instantiated exactly once; all capability names for that class point to the same instance.

- **Per-JAR URLClassLoader isolation**: Both `JarHandlerLoader` and `LifecycleHandlerLoader` create one `URLClassLoader` per JAR. The parent is set to `EventHandler.class.getClassLoader()` (for the POJO loader) or `HandlerLifecycle.class.getClassLoader()` (for the lifecycle loader), so core types are shared while each JAR's dependencies remain isolated.

- **Abstract class for lifecycle extensibility**: `HandlerLifecycle` is an abstract class rather than an interface so that future versions can add methods (health checks, config reload) with default implementations without breaking existing implementations.

- **Companion factory on interface**: `LifecycleContext.of(Map, Dispatcher)` is a static factory method on the interface itself, following the companion factory pattern. `configuration` must not be null (validated immediately with `Objects.requireNonNull`); `dispatcher` may be null when call-time injection is used.

### 3 Coding problems

- **YAML configuration loading not implemented**: `LifecycleHandlerLoader.loadConfiguration()` always returns `Map.of()`. The `TODO` comment references SnakeYAML. Handlers that read from `LifecycleContext.getConfiguration()` will always receive an empty map until this is implemented. Status: open.

- **JarHandlerLoader URLClassLoaders not explicitly closed**: `JarHandlerLoader.close()` is a no-op; the per-JAR `URLClassLoader` instances created during `processJar()` are not tracked and are not closed explicitly. They are garbage-collected when no longer reachable, which is acceptable for the current POJO-handler use case but means JAR file handles are held until GC. Status: known limitation.

- **JAR paths have no resolution strategy**: Both loaders accept `Collection<Path>` directly. Callers are responsible for resolving absolute vs relative paths. No built-in classpath-relative or home-directory-relative resolution. Status: known limitation.

### 4 Release notes

- 2026-03-24 / dino: Moved `Dispatcher` to call-time injection; `LifecycleContext.of` now accepts null dispatcher. `LifecycleContext.getDispatcher()` throws `IllegalStateException` when no dispatcher was provided at init time.
- 2026-03-24 / dino: Added inline lifecycle path — `HandlerLifecycle` subclasses can be wired without JAR loading, sharing the application class loader.
- 2026-02-19 / dino: Added `HandlerLifecycle` abstract class and `LifecycleContext` interface; added `LifecycleHandlerLoader` for framework-integrated handlers.
- 2026-02-18 / dino: Added multi-capability handler support via `@Capabilities` container annotation and `getAnnotationsByType`; single instance shared across all capability registrations for the same class.
- 2026-02-18 / dino: Added `Dispatcher.send(Event, BiFunction, long)` default method as synchronous convenience wrapper with distinct exception handling per failure mode.
- 2026-02-16 / dino: Removed `Serializable` constraint from `Event.payload`; field is now `Object` to accept JSON-compatible values without artificial serialisation requirement.
- 2026-02-16 / dino: Added `JarHandlerLoader` with per-JAR `URLClassLoader` isolation and two-phase discovery/instantiation.

### 5 AI Invariants: correctness, precision, testability

- `EventBob.close()` must be called only after the caller is done dispatching; calling `processEvent` after `close()` submits to a shutdown executor and the returned `CompletableFuture` may never complete normally.
- `LifecycleContext.of(null, ...)` throws `NullPointerException` at the call site; callers must pass `Map.of()` for empty configuration.
- `LifecycleContext.getDispatcher()` throws `IllegalStateException` when the loader was constructed with a null dispatcher; handlers that rely on dispatch during `initialize()` will fail unless the loader is constructed with a non-null dispatcher.
- `JarHandlerLoader` and `LifecycleHandlerLoader` detect duplicate capability names with `IllegalStateException`; this is a fail-fast contract violation, not a recoverable error.
- `LifecycleHandlerLoader.close()` shuts down lifecycles before closing class loaders; reversing this order would cause `ClassNotFoundException` or `NoClassDefFoundError` during handler shutdown code that references classes in the JAR.
- `EventBob.dispatcher` is a method reference (`this::processEvent`) created at construction; it is safe to share across threads because `processEvent` is stateless with respect to the dispatcher reference.
- Test stubs for `EventHandler` should record the received `Event` and `Dispatcher` rather than using mocking frameworks; state-recording stubs are simpler and reveal exactly what the router passed.
