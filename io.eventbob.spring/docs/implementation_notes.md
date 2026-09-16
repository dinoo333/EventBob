# io.eventbob.spring — Implementation Notes

### 1 Frameworks used

- **Jackson (`com.fasterxml.jackson.databind`)**: Used only in `EventDto` (Jackson annotations `@JsonCreator`, `@JsonProperty`) and `HttpEventHandlerAdapter` (bare `ObjectMapper` for serialisation/deserialisation). Not present in any core or domain class — contained entirely within the infrastructure adapter layer.
- **WireMock**: Used in integration tests for `HttpEventHandlerAdapter` to stub remote HTTP endpoints. Not present in production code.
- **SLF4J**: Used for structured logging in `EventBobConfig` (`LoggerFactory.getLogger`). Concrete binding is provided by the consuming Spring Boot application.
- **`jakarta.annotation.PreDestroy`**: Used in `EventBobConfig.shutdownInlineLifecycles()` for Spring-managed lifecycle teardown of inline handler lifecycles. Requires Jakarta EE annotations on the classpath (provided by Spring Boot).

### 2 Coding patterns

- **Library configuration with optional autowiring**: `EventBobConfig` is a `@Configuration` class with no `@SpringBootApplication`. All three constructor parameters (`List<Path> handlerJarPaths`, `List<HandlerLifecycle> inlineLifecycles`, `List<RemoteCapability> remoteCapabilities`) are annotated `@Autowired(required = false)` and default to `List.of()` when absent. This allows concrete applications to supply only the beans relevant to their deployment without errors for missing beans.

- **`@PreDestroy` for inline lifecycle teardown**: `EventBobConfig.shutdownInlineLifecycles()` is annotated `@PreDestroy` so Spring calls it when the application context closes. Each inline lifecycle's `shutdown()` method is called in a try/catch loop; failures are logged at WARN and do not prevent subsequent lifecycles from shutting down.

- **Boundary object (EventDto)**: `EventDto` is a Java record in `io.eventbob.spring.adapter`. It carries `@JsonCreator` and `@JsonProperty` so the core `Event` class remains framework-agnostic. `null` parameters and metadata maps in the JSON are normalised to `Collections.emptyMap()` in the explicit canonical constructor. Translation is `EventDto.fromEvent(Event)` and `eventDto.toEvent()`.

- **Location-transparent remote handler**: `HttpEventHandlerAdapter` implements `EventHandler`. From `EventBob`'s perspective it is indistinguishable from a local handler. It performs a synchronous blocking HTTP POST inside `handle()`, which is already called on a virtual thread by `EventBob`. `InterruptedException` during the HTTP call restores the interrupt flag before re-throwing as `EventHandlingException`.

- **Null-dispatcher call-time injection in `EventBobConfig`**: Inline lifecycles are initialised with `LifecycleContext.of(Map.of(), null)`. The null dispatcher is intentional: handlers receive the dispatcher as a parameter of `handle(Event, Dispatcher)` at event-processing time. JAR-based loaders are constructed with `HandlerLoader.lifecycleLoader(handlerJarPaths, null)` for the same reason.

- **Fail-fast duplicate capability detection across sources**: `EventBobConfig.loadAllHandlers()` checks for duplicate capability names across inline lifecycles, JAR handlers, and remote handlers. The first duplicate found throws `IllegalStateException`, which propagates out of the `@Bean eventBob(...)` method and prevents application startup.

### 3 Coding problems

- **`EventController` uses null error callback**: `eventBob.processEvent(event, (error, originalEvent) -> null)` passes a null-returning lambda as the error handler. EventBob falls back to `DefaultErrorEvent.create()` on any error. HTTP clients therefore always receive a 200 OK with an error event payload rather than an HTTP error status code. This may be surprising to HTTP clients expecting 4xx or 5xx responses for handler errors. Status: open, known design gap.

- **`HttpEventHandlerAdapter.objectMapper` is a private instance field**: Each `HttpEventHandlerAdapter` instance creates its own `ObjectMapper`. For the current number of remote capabilities this is harmless, but `ObjectMapper` creation is moderately expensive and the instances are not shared. Status: known minor inefficiency.

- **No timeout configured on `HttpClient` bean**: The default `HttpClient` bean in `EventBobConfig` specifies only `HTTP_1_1` version; no connect timeout or request timeout is set. Remote calls can block indefinitely if the remote endpoint is unreachable. Applications can override the bean to add timeouts. Status: open, requires application-level override.

- **`healthcheck` capability silent overwrite**: `healthcheck` is registered before other handlers; a capability named `healthcheck` loaded from any other source would silently overwrite it without triggering duplicate detection — the uniqueness invariant is not enforced for this built-in capability name.

### 4 Release notes

- 2026-03-24 / dino: Added inline lifecycle registration path to `EventBobConfig`; `List<HandlerLifecycle>` constructor parameter collects `HandlerLifecycle` beans; `@PreDestroy shutdownInlineLifecycles()` added for clean teardown.
- 2026-03-24 / dino: Moved Dispatcher to call-time injection; both inline and JAR-based loaders now pass null dispatcher to `LifecycleContext`.
- 2026-02-18 / dino: Added `HttpEventHandlerAdapter`, `RemoteCapability`, `RemoteHandlerLoader`; remote capabilities registered in `EventBobConfig` alongside local handlers with duplicate detection.
- 2026-02-16 / dino: Added `EventDto` record with `@JsonCreator`/`@JsonProperty` to isolate Jackson annotations from core `Event`; added `EventController` with `CompletableFuture<EventDto>` return type.
- 2026-02-15 / dino: Initial module — `EventBobConfig`, `EventController`, `HealthcheckHandler`.

### 5 AI Invariants: correctness, precision, testability

- `EventBobConfig` must not be instantiated with a non-null dispatcher in the lifecycle context; passing a non-null dispatcher here would allow handlers to call `context.getDispatcher()` during `initialize()`, but the `EventBob` bean may not yet be fully constructed at that point, creating a circular dependency risk.
- `HttpEventHandlerAdapter.handle()` is synchronous and blocking; it must only be called from within an `EventBob` handler invocation (i.e. on a virtual thread). Calling it on the Spring MVC request thread would block that thread.
- Duplicate capability names across inline lifecycles, JAR handlers, and remote handlers are detected in `loadAllHandlers()` and cause `IllegalStateException` at startup. No partial registration occurs — the `EventBob` bean is not returned if any duplicate is found.
- `healthcheck` is a reserved capability name; duplicate detection must cover it on the same basis as all other capabilities.
- `EventDto` normalises null maps to `Collections.emptyMap()` at record construction time; downstream `toEvent()` passes these directly to `Event.Builder.parameters()` and `Event.Builder.metadata()`, which accept empty maps without error.
