# EventBob Implementation Notes

## What This File Contains

Implementation patterns and conventions for EventBob modules. For domain understanding, see `domain_spec.md`. For architecture, see `architecture.md`.

---

## Module-Specific Notes

See module-level implementation_notes.md files for detailed patterns:
- `io.eventbob.core/implementation_notes.md` - Core framework patterns
- `io.eventbob.spring/implementation_notes.md` - Spring Boot integration patterns (work-in-progress)

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

## Open Questions

1. **Transport layer:** How do external clients invoke capabilities? HTTP adapter? gRPC? Message queue listener? (Work-in-progress)
2. **Capability discovery:** Dynamic JAR scanning vs static configuration? (To be determined)
3. **Error handling:** Standardized error event format? Custom error handlers per transport? (To be determined)
