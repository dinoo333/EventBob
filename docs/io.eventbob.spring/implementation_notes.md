# io.eventbob.spring Implementation Notes

## Module Patterns

### Spring Boot Application Pattern
- `@SpringBootApplication` entry point
- Standard Spring Boot lifecycle
- Configuration via `application.properties`

### Dependency Injection
- `@Configuration` classes define beans
- EventBob instance created as Spring bean
- Handlers registered via EventBob builder

### Handler Implementation
- Handlers implement core `EventHandler` interface
- Annotated with `@Capability("target-name")`
- Registered with EventBob at configuration time

## Testing Conventions

### Unit Tests
- JUnit 5 + AssertJ (matching core conventions)
- `should*` naming pattern
- Test event preservation (source, target, metadata, parameters)
- Test payload transformation logic

### Integration Tests
- To be determined as implementation evolves

## Code Quality

### Current State
- Minimal bootstrap implementation
- Clean baseline (no technical debt)
- Following core patterns and conventions

### Known Gaps
- Transport layer work-in-progress
- Configuration patterns evolving
- Integration test strategy to be determined

## Work-in-Progress

This module is under active development. Patterns and implementation details are subject to change as requirements clarify and the system evolves.
