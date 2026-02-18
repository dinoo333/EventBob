# <img src="images/eventbob.jpeg" alt="EventBob Logo" width="100"> EventBob

Event routing framework with clean architecture for building microliths with location transparency.

## What Is EventBob?

EventBob solves the chatty-application anti-pattern by bundling microservices into a microlith. When too many microservices cause network saturation, EventBob loads their JARs into a single server for in-process communication.

**Location Transparency:** EventBob also supports remote handler loading via HTTP. Handlers can run in-process (loaded from JARs) or remotely (accessed via HTTP endpoints). The routing layer treats both identically - clients cannot tell the difference.

## Quick Start

### 1. Add Dependencies

```xml
<dependency>
  <groupId>io.eventbob</groupId>
  <artifactId>io.eventbob.spring</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Write a Handler

```java
@Capability("echo")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    return event.toBuilder()
        .payload(event.getPayload())
        .build();
  }
}
```

### 3. Configure and Run

```java
@SpringBootApplication
@Import(EventBobConfig.class)
public class MyMicrolith {

  public static void main(String[] args) {
    SpringApplication.run(MyMicrolith.class, args);
  }

  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Paths.get("handlers/echo-handler.jar")
    );
  }
}
```

### 4. Send Events

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "client",
    "target": "echo",
    "payload": "hello world"
  }'
```

Response:
```json
{
  "source": "client",
  "target": "echo",
  "payload": "hello world"
}
```

## Architecture

EventBob follows Clean Architecture with three layers:

```
Core (Domain Model)
    io.eventbob.core
    - Event, EventHandler, EventBob
    - Zero external dependencies
    - Framework-agnostic
            ↑
            │
Infrastructure Library
    io.eventbob.spring
    - Spring Boot wiring
    - HTTP adapters
    - EventController REST endpoint
            ↑
            │
Application (Concrete Microlith)
    io.eventbob.example.microlith.spring.echo
    - Spring Boot main class
    - Handler JAR configuration
    - Remote endpoint configuration
```

**Dependency Rule:** Each layer depends only on layers inside it. Core is pure domain logic with zero framework dependencies.

## Module Structure

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `io.eventbob.core` | Domain model and routing logic | JDK only |
| `io.eventbob.spring` | Spring Boot infrastructure library | core + Spring Boot |
| `io.eventbob.example.echo` | Echo handler implementation | core |
| `io.eventbob.example.lower` | Lower handler implementation | core |
| `io.eventbob.example.upper` | Upper handler implementation | core |
| `io.eventbob.example.microlith.spring.echo` | Echo microlith (Spring Boot app) | spring + echo + lower handlers |
| `io.eventbob.example.microlith.spring.upper` | Upper microlith (Spring Boot app) | spring + upper handler |

## Location Transparency

EventBob provides location transparency - remote and local handlers are indistinguishable to client code.

### Local Handler (In-Process)

```java
@Bean
public List<Path> handlerJarPaths() {
  return List.of(
    Paths.get("handlers/echo.jar"),
    Paths.get("handlers/lower.jar")
  );
}
```

Handlers are loaded into the same JVM and called directly.

### Remote Handler (HTTP)

```java
@Bean
public List<RemoteCapability> remoteCapabilities() {
  return List.of(
    new RemoteCapability("upper", URI.create("http://localhost:8081"))
  );
}
```

The "upper" capability is proxied to a remote EventBob server via HTTP. Client code is unaware of the location.

### Example: Echo Microlith Calling Upper Microlith

The echo microlith loads echo and lower handlers locally, and delegates upper capability to a remote microlith:

```java
@SpringBootApplication
@Import(EventBobConfig.class)
public class EchoApplication {

  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
      Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar"),
      Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar")
    );
  }

  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
      new RemoteCapability("upper", URI.create("http://localhost:8081"))
    );
  }
}
```

From the echo microlith, you can:

```bash
# Call local echo handler (in-process)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"source": "client", "target": "echo", "payload": "hello"}'

# Call local lower handler (in-process)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"source": "client", "target": "lower", "payload": "HELLO"}'

# Call remote upper handler (HTTP to port 8081)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"source": "client", "target": "upper", "payload": "hello"}'
```

All three calls look identical from the client perspective. The routing layer handles location transparently.

## Key Concepts

### Event

Immutable domain object representing a message to be routed:

```java
Event event = Event.builder()
    .source("client")
    .target("echo")
    .payload("hello world")
    .build();
```

### EventHandler

Interface for implementing capabilities:

```java
public interface EventHandler {
  Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException;
}
```

### Capability

Annotation marking handlers with their capability name:

```java
@Capability("echo")
public class EchoHandler implements EventHandler {
  // implementation
}
```

### Dispatcher

Interface for dispatching events to other capabilities (used for chaining):

```java
@Capability("uppercase-and-reverse")
public class CompositeHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    Event uppercased = dispatcher.send(
      event.toBuilder().target("upper").build(),
      (error, evt) -> null
    ).join();
    return dispatcher.send(
      uppercased.toBuilder().target("reverse").build(),
      (error, evt) -> null
    ).join();
  }
}
```

### HandlerLoader

Interface for loading handlers from different sources:

```java
// Load from JARs (in-process)
HandlerLoader jarLoader = HandlerLoader.jarLoader(jarPaths);

// Load from remote endpoints (HTTP)
HandlerLoader remoteLoader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
```

## Building and Testing

### Build All Modules

```bash
mvn clean install
```

### Run Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Build Handler JARs

Handler JARs must be built before running microliths:

```bash
# Build all handler JARs
mvn clean package -pl io.eventbob.example.echo,io.eventbob.example.lower,io.eventbob.example.upper
```

### Run Example Microliths

Start the upper microlith (port 8081):

```bash
cd io.eventbob.example.microlith.spring.upper
mvn spring-boot:run
```

In another terminal, start the echo microlith (port 8080):

```bash
cd io.eventbob.example.microlith.spring.echo
mvn spring-boot:run
```

Test the setup:

```bash
# Local echo handler
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"source": "client", "target": "echo", "payload": "test"}'

# Remote upper handler (proxied to port 8081)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"source": "client", "target": "upper", "payload": "test"}'
```

## Documentation

- [Architecture Details](docs/architecture.md) - Clean architecture layers, dependency rules, and location transparency
- [Spring Implementation](io.eventbob.spring/README.md) - Spring Boot infrastructure library documentation

## Design Principles

EventBob follows these principles:

1. **Clean Architecture** - Core domain has zero framework dependencies
2. **Dependency Inversion** - Dependencies point inward toward the domain
3. **Location Transparency** - Remote and local handlers are indistinguishable
4. **Interface Segregation** - Small, focused interfaces (EventHandler, Dispatcher, HandlerLoader)
5. **Bridge Pattern** - Domain abstractions enable multiple infrastructure implementations

## Technology Stack

- Java 21
- Spring Boot 3.2.2 (infrastructure layer only)
- Maven (multi-module build)
- JUnit 5 + AssertJ + Mockito (testing)

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
