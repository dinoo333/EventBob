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

### REST Adapter Pattern

**Applied in:** EventController

**Pattern:**
- Exposes POST /events endpoint for HTTP event submission
- EventDto serves as boundary object (prevents Jackson annotations in domain)
- Maps EventDto ↔ Event with direct field assignment (no type conversion)
- Returns `CompletableFuture<EventDto>` for non-blocking HTTP responses
- Spring MVC handles async suspension automatically

**Mapping strategy:**
- EventDto and Event use identical `Object` types for parameters/metadata/payload
- No type conversion helpers needed (direct pass-through)
- Transformation: `Event → EventBob.processEvent() → thenApply(Event → EventDto)`

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

## JAR Loading Integration

### EventBobConfig Bean Configuration

**Applied in:** EventBobConfig

**Pattern:**
- Uses `HandlerLoader.jarLoader()` to obtain default loader implementation
- Loads handlers at Spring context initialization (eager loading)
- Registers loaded handlers via `EventBob.Builder.handler(String, EventHandler)`
- Includes hard-coded healthcheck handler alongside JAR-loaded handlers

**JAR Path Configuration:**
- Currently hard-coded in `loadHandlersFromJars()` method
- Paths: relative to working directory (project root)
  - `io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar`
  - `io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar`
- **Technical debt:** Should be externalized to `application.properties` for production use

**Working Directory Dependency:**
- JAR paths are relative to project root
- Application must be started from project root directory
- **Limitation:** Breaks if started from different working directory
- **Future:** Externalize to configuration with absolute paths or classpath-relative resolution

**Error Handling:**
- `IOException` from loader wrapped in `IllegalStateException` (fails application startup)
- Logged at ERROR level via SLF4J
- Fail-fast approach: Better to fail at startup than serve with missing capabilities

**Logging:**
- Success: INFO log with count of registered handlers from JARs
- Failure: ERROR log with exception details, then throws `IllegalStateException`

**Design Decisions:**
- Eager loading: Handlers loaded at context init (fast failure, predictable startup)
- Mixed registration: Hard-coded and JAR-based handlers coexist (healthcheck stays hard-coded)
- Fail-fast: Missing/malformed JARs prevent application startup (better than partial functionality)

**Known Technical Debt:**
- Hard-coded JAR paths (should be `spring.eventbob.handler-jars` in application.properties)
- Working directory dependency (should be absolute paths or classpath-relative)
- No JAR path validation before attempting load (should check existence earlier)

## Work-in-Progress

This module is under active development. Patterns and implementation details are subject to change as requirements clarify and the system evolves.
