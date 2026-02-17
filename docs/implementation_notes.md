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
- `HandlerLoader` interface with factory method `HandlerLoader.jarLoader()`
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

## Open Questions

1. **Transport layer:** How do external clients invoke capabilities? HTTP adapter? gRPC? Message queue listener? (Partially resolved: HTTP REST adapter implemented in io.eventbob.spring)
2. **Error handling:** Standardized error event format? Custom error handlers per transport? (To be determined)
3. **Remote invocation:** How do microliths invoke capabilities on other microliths? (To be determined)
