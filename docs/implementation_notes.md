# EventBob Implementation Notes

## What This File Contains

Implementation patterns and conventions for EventBob modules. For domain understanding, see `domain_spec.md`. For architecture, see `architecture.md`.

---

## Module-Specific Notes

See module-level implementation_notes.md files for detailed patterns:
- `io.eventbob.core/implementation_notes.md` - Core framework patterns, JAR loading implementation
- `io.eventbob.spring/implementation_notes.md` - Spring Boot library integration patterns
- `io.eventbob.example.microlith.spring.echo/implementation_notes.md` - Example microlith application demonstrating Spring Boot + JAR loading

---

## General Patterns

### Immutability

Value objects use immutable design with builders:
- `Event.builder()` for construction
- `event.toBuilder()` for copy-on-write modification

### Handler Pattern

Handlers implement `EventHandler` interface:
- Annotated with `@Capability("target-name")`
- Single method: `Event handle(Event event, Dispatcher dispatcher)`
- Stateless (no instance fields beyond injected dependencies)

### Testing Conventions

- JUnit 5 + AssertJ
- `should*` naming convention for test methods
- Test doubles as needed (no heavy mocking frameworks)
- One test per behavioral assertion

---

## Design Philosophy

EventBob follows a **simple, focused design:**
- Core is framework-agnostic (zero external dependencies)
- Infrastructure adapters depend on core, never reverse
- Favor composition over inheritance
- Fail-fast during bootstrap, resilient at runtime

---

## JAR Loading Implementation

**Resolved approach:** Dynamic JAR scanning using URLClassLoader (one per JAR).

**Pattern:**
- `HandlerLoader` interface with constructor injection pattern
- Factory method `HandlerLoader.jarLoader(Collection<Path>)` returns JAR-based implementation
- Parameterless `loadHandlers()` method uses constructor-injected dependencies
- `JarHandlerLoader` implementation (package-private)
- Separate URLClassLoader per JAR, parent = `EventHandler.class.getClassLoader()`
- Reflection-based handler instantiation via no-args constructor
- Malformed JARs logged as warnings and skipped (ZipException detection)
- Resource management: URLClassLoader in try-with-resources

**Trade-offs:**
- Isolation: Each JAR gets its own class loader (prevents dependency conflicts)
- Shared core: EventHandler and core classes visible to all handlers via parent delegation
- Reflection cost: Instantiation uses `getDeclaredConstructor().newInstance()` (acceptable for bootstrap)
- Working directory dependency: JAR paths currently relative to project root (needs externalization)

---

## Remote Invocation Implementation

**Resolved approach:** HTTP-based remote handler adapters with location transparency.

**Pattern:**
- `RemoteCapability` record maps capability names to remote endpoint URIs
- `RemoteHandlerLoader` creates `HttpEventHandlerAdapter` instances for each remote capability
- `HttpEventHandlerAdapter` implements `EventHandler` interface, delegates to remote HTTP endpoint
- `EventDto` (Java Record) translates between domain `Event` and JSON for HTTP communication
- EventBob treats remote handlers identically to local handlers (location transparency)

**Communication protocol:**
- HTTP POST to `{remoteEndpoint}/events`
- Content-Type: application/json
- Request/response body: EventDto serialized to JSON
- HTTP status codes: 2xx = success, 4xx/5xx = error

**Configuration:**
- `EventBobConfig` accepts `List<RemoteCapability>` via constructor (optional, autowired)
- Applications provide `@Bean List<RemoteCapability>` to register remote capabilities
- Duplicate capability names across local and remote handlers detected and fail-fast

---

## Transport Layer

**HTTP REST adapter implemented in io.eventbob.spring:**
- EventController exposes POST /events endpoint
- EventDto serves as boundary object (keeps Jackson annotations out of domain)
- Non-blocking: returns `CompletableFuture<EventDto>`
- Spring MVC handles async suspension automatically

**Error handling:**
- Errors converted to error events at EventBob level
- HTTP adapter propagates errors as HTTP 4xx/5xx status codes
- Network errors and timeouts throw `EventHandlingException`
