# io.eventbob.spring Implementation Notes

## Module Purpose

This module is a **library** that provides Spring Boot integration for EventBob. It does not contain a Spring Boot application main class. Concrete applications import this module and provide configuration.

## Module Patterns

### Library Pattern
- No `@SpringBootApplication` entry point (applications provide this)
- Reusable `@Configuration` classes
- Concrete applications provide handler JAR paths via dependency injection

### Dependency Injection
- `@Configuration` classes define beans
- EventBob instance created as Spring bean
- Handlers registered via EventBob builder
- Constructor injection for handler JAR paths: `EventBobConfig(List<Path> handlerJarPaths)`

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
- Clean library implementation
- No technical debt
- Following core patterns and conventions
- Constructor injection for configuration (no hard-coded paths)

### Known Gaps
- Integration test strategy to be determined
- Remote invocation support (planned for future)

## JAR Loading Integration

### EventBobConfig Bean Configuration

**Applied in:** EventBobConfig

**Pattern:**
- Constructor injection: `EventBobConfig(List<Path> handlerJarPaths)`
- Concrete applications provide `List<Path>` bean with JAR paths to load
- Uses `HandlerLoader.jarLoader()` to obtain default loader implementation
- Loads handlers at Spring context initialization (eager loading)
- Registers loaded handlers via `EventBob.Builder.handler(String, EventHandler)`
- Includes healthcheck handler (always registered, not loaded from JARs)

**JAR Path Configuration:**
- Applications provide `@Bean public List<Path> handlerJarPaths()` method
- Example (from io.eventbob.example.microlith.spring.echo):
  ```java
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar"),
      Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar")
    );
  }
  ```
- JAR paths are application-specific (this library is agnostic)
- Applications decide path resolution strategy (relative, absolute, classpath)

**Error Handling:**
- `IOException` from loader wrapped in `IllegalStateException` (fails application startup)
- Logged at ERROR level via SLF4J
- Fail-fast approach: Better to fail at startup than serve with missing capabilities

**Logging:**
- Success: INFO log with count of registered handlers from JARs
- Failure: ERROR log with exception details, then throws `IllegalStateException`

**Design Decisions:**
- Constructor injection: Clean dependency inversion (library depends on abstraction, applications provide concrete paths)
- Eager loading: Handlers loaded at context init (fast failure, predictable startup)
- Mixed registration: Healthcheck handler hard-coded, other handlers JAR-loaded
- Fail-fast: Missing/malformed JARs prevent application startup (better than partial functionality)
- Library stays generic: No knowledge of specific handlers or paths

## Usage Pattern

To use this library in a Spring Boot application:

1. Add dependency in pom.xml:
   ```xml
   <dependency>
     <groupId>io.eventbob</groupId>
     <artifactId>io.eventbob.spring</artifactId>
     <version>${project.version}</version>
   </dependency>
   ```

2. Import EventBobConfig:
   ```java
   @SpringBootApplication
   @Import(EventBobConfig.class)
   public class MyApplication {
     // ...
   }
   ```

3. Provide handler JAR paths bean:
   ```java
   @Bean
   public List<Path> handlerJarPaths() {
     return List.of(
       Paths.get("path/to/handler.jar")
     );
   }
   ```

4. EventBob instance is automatically available as Spring bean
5. POST events to `/events` endpoint

See `io.eventbob.example.microlith.spring.echo` for complete example.
