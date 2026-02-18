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
- Constructor injection for handler sources: `EventBobConfig(List<Path> handlerJarPaths, List<RemoteCapability> remoteCapabilities)`
- Remote capabilities are optional (autowired with `required = false`)

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
- EventDto is a Java Record with Jackson annotations for JSON serialization/deserialization
- Uses `@JsonCreator` and `@JsonProperty` for proper Jackson binding
- Translation methods: `EventDto.fromEvent(Event)` and `eventDto.toEvent()`
- Direct field mapping (no complex type conversion)
- Null-safe: null parameters/metadata become empty maps in EventDto constructor
- Transformation: `EventDto → toEvent() → EventBob.processEvent() → thenApply(fromEvent())`

**EventDto Pattern:**
```java
public record EventDto(
    String source,
    String target,
    Map<String, Object> parameters,
    Map<String, Object> metadata,
    Object payload
) {
  public static EventDto fromEvent(Event event) { ... }
  public Event toEvent() { ... }
}
```

## Testing Conventions

### Unit Tests
- JUnit 5 + AssertJ (matching core conventions)
- `should*` naming pattern
- Test event preservation (source, target, metadata, parameters)
- Test payload transformation logic

### Integration Tests
- WireMock for testing HttpEventHandlerAdapter
- TestContainers can be used for full Spring Boot integration tests
- HttpEventHandlerAdapterTest demonstrates WireMock stubbing pattern

## Code Quality

### Current State
- Clean library implementation
- No technical debt
- Following core patterns and conventions
- Constructor injection for configuration (no hard-coded paths)
- EventDto keeps Jackson annotations out of domain layer
- Location transparency for remote handlers (HttpEventHandlerAdapter implements EventHandler)

## Remote Invocation Implementation

### RemoteCapability Configuration

**Applied in:** RemoteCapability (Java Record)

**Pattern:**
- Java Record with validation in compact constructor
- Maps capability name to remote endpoint URI
- Immutable and type-safe configuration
- Validation: name must not be null or blank, URI must not be null

**Example:**
```java
@Bean
public List<RemoteCapability> remoteCapabilities() {
  return List.of(
    new RemoteCapability("upper", URI.create("http://localhost:8081")),
    new RemoteCapability("email", URI.create("http://email-service:8080"))
  );
}
```

### HttpEventHandlerAdapter

**Applied in:** HttpEventHandlerAdapter

**Pattern:**
- Implements `EventHandler` interface (location transparency)
- Wraps HTTP calls to remote EventHandler endpoints
- Constructor injection: `HttpEventHandlerAdapter(URI remoteEndpoint, HttpClient httpClient)`
- Uses EventDto for JSON serialization/deserialization
- Synchronous HTTP calls (blocking within handler invocation)
- Converts HTTP errors to `EventHandlingException`

**HTTP Protocol:**
- POST `{remoteEndpoint}/events`
- Content-Type: application/json
- Request body: EventDto serialized to JSON
- Response body: EventDto deserialized from JSON
- HTTP status codes: 2xx = success, 4xx = client error, 5xx = server error

**Error Handling:**
- Network errors (IOException): throw `EventHandlingException` with cause
- Interrupted requests: restore interrupt flag, throw `EventHandlingException`
- HTTP 4xx/5xx: throw `EventHandlingException` with status code and response body
- Serialization failures: throw `EventHandlingException` with cause

### RemoteHandlerLoader

**Applied in:** RemoteHandlerLoader

**Pattern:**
- Implements `HandlerLoader` interface
- Constructor injection: `RemoteHandlerLoader(List<RemoteCapability> remoteCapabilities, HttpClient httpClient)`
- Creates `HttpEventHandlerAdapter` instances for each remote capability
- Parameterless `loadHandlers()` returns map of capability names to adapters
- No I/O during loading (adapters created, not invoked)

**Design:**
- Location transparency: Remote handlers indistinguishable from local handlers to EventBob
- Extensibility: Future transport mechanisms (gRPC, JMS, Kafka) can be added by inspecting URI scheme
- Dependency injection: HttpClient provided by Spring configuration, reused across all adapters

### HttpClient Bean Configuration

**Applied in:** EventBobConfig

**Pattern:**
- HttpClient created as Spring bean
- Configuration: HTTP/1.1 version (standard compatibility)
- Shared across all HttpEventHandlerAdapter instances
- Applications can override bean to customize timeout, proxy, SSL, etc.

**Example override:**
```java
@Bean
public HttpClient httpClient() {
  return HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(5))
      .build();
}
```

## JAR Loading Integration

### EventBobConfig Bean Configuration

**Applied in:** EventBobConfig

**Pattern:**
- Constructor injection: `EventBobConfig(List<Path> handlerJarPaths, List<RemoteCapability> remoteCapabilities)`
- Remote capabilities are optional (autowired with `required = false`, defaults to empty list)
- Concrete applications provide `List<Path>` bean with JAR paths to load
- Optionally provide `List<RemoteCapability>` bean for remote handler endpoints
- Uses `HandlerLoader.jarLoader(handlerJarPaths)` to obtain JAR-based loader implementation
- Uses `RemoteHandlerLoader(remoteCapabilities, httpClient)` to obtain remote loader implementation
- Loads handlers at Spring context initialization (eager loading)
- Merges handlers from both sources (JAR and remote)
- Detects and fails on duplicate capability names across sources
- Registers all loaded handlers via `EventBob.Builder.handler(String, EventHandler)`
- Includes healthcheck handler (always registered, not loaded from external sources)

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
- Mixed registration: Healthcheck handler hard-coded, other handlers loaded from JARs or remote endpoints
- Fail-fast: Missing/malformed JARs or duplicate capabilities prevent application startup (better than partial functionality)
- Library stays generic: No knowledge of specific handlers or paths
- HttpClient bean created by config for use by remote loaders
- Remote capabilities optional: Applications without remote handlers need not provide the bean

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

4. (Optional) Provide remote capabilities bean for inter-microlith communication:
   ```java
   @Bean
   public List<RemoteCapability> remoteCapabilities() {
     return List.of(
       new RemoteCapability("upper", URI.create("http://localhost:8081"))
     );
   }
   ```

5. EventBob instance is automatically available as Spring bean
6. POST events to `/events` endpoint

See `io.eventbob.example.microlith.spring.echo` for complete example.
