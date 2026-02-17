# io.eventbob.spring Architecture

## Module Purpose

Spring Boot infrastructure library providing server deployment, handler wiring, and transport adapters for EventBob.

**This is a library module.** It has no main class, no hard-coded configuration. Concrete microlith applications import this library and provide configuration via constructor injection.

---

## Layer Assignment

This module is in the **infrastructure library layer** (middle):
- Depends on `io.eventbob.core` (domain abstractions)
- Provides framework-specific implementations
- Imported by microlith application modules (outermost layer)
- Core never depends on this module (dependency inversion)

**Dependency direction:**
```
io.eventbob.example.microlith.*  →  io.eventbob.spring  →  io.eventbob.core
(applications)                       (infrastructure)      (domain)
```

---

## Key Components

### Configuration

**EventBobConfig:**
- Spring dependency injection wiring
- EventBob instance configuration with handlers
- **Constructor injection:** Accepts `List<Path> handlerJarPaths` from application layer

**Constructor injection pattern:**
```java
@Configuration
public class EventBobConfig {
  private final List<Path> handlerJarPaths;

  /**
   * Create EventBob configuration with specified handler JAR paths.
   * Applications must provide a List<Path> handlerJarPaths bean.
   */
  public EventBobConfig(List<Path> handlerJarPaths) {
    this.handlerJarPaths = handlerJarPaths;
  }

  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler) {
    EventBob.Builder builder = EventBob.builder();
    builder.handler("healthcheck", healthcheckHandler);
    
    Map<String, EventHandler> handlers = loadHandlersFromJars();
    handlers.forEach(builder::handler);
    
    return builder.build();
  }

  private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
    HandlerLoader loader = HandlerLoader.jarLoader();
    return loader.loadHandlers(handlerJarPaths);
  }
}
```

**Key design decision:** JAR paths are injected via constructor, not hard-coded. This makes the library reusable across different microlith deployments. Applications decide which handlers to load.

**HandlerLoader dependency:**
Spring depends only on `HandlerLoader` interface (public API), never on `JarHandlerLoader` (package-private implementation). This is Dependency Inversion Principle in action.

### Handlers

**HealthcheckHandler:**
- Built-in system handler
- Registered with capability name "healthcheck"
- Implements EventHandler interface from core
- Returns system health status

**Design rationale:** Healthcheck is infrastructure-specific (checks Spring context health, JVM metrics, etc.), so it lives in infrastructure layer, not core.

### Transport Adapters

**EventController:**
- REST endpoint adapter
- Bridges HTTP requests to Event model
- Routes to EventBob
- Converts Event responses back to HTTP

**EventDto:**
- HTTP/Event boundary DTO
- Isolates HTTP representation from domain Event
- Prevents framework types from leaking into core

**Pattern:**
```java
@RestController
public class EventController {
    private final EventBob eventBob;
    
    @PostMapping("/events")
    public ResponseEntity<EventDto> handleEvent(@RequestBody EventDto request) {
        Event event = request.toEvent();
        CompletableFuture<Event> response = eventBob.processEvent(event);
        return ResponseEntity.ok(EventDto.fromEvent(response.get()));
    }
}
```

---

## Design Principles

### Bridge Pattern

Infrastructure depends on core abstractions:
- EventHandler (interface)
- Event (domain model)
- EventBob (router)
- HandlerLoader (loading abstraction)

Core remains framework-agnostic. Spring types stay in this module, never leak into core.

### Dependency Inversion

**Traditional (violated):**
```
Spring Config → JarHandlerLoader (concrete class)
```

**Actual (DIP compliant):**
```
Spring Config → HandlerLoader (interface) ← JarHandlerLoader (hidden)
```

The factory method pattern (`HandlerLoader.jarLoader()`) hides the concrete implementation from Spring. Spring never imports JarHandlerLoader.

### Library Module Pattern

This module is a **library**, not an application:
- No `main()` method
- No hard-coded configuration
- No Spring Boot Application class
- No `spring-boot-maven-plugin` in pom.xml

Applications import this library and provide:
- Spring Boot main class (`@SpringBootApplication`)
- Configuration beans (`List<Path> handlerJarPaths`)
- Deployment properties (`application.properties`)

---

## How Applications Use This Library

**Step 1:** Application depends on io.eventbob.spring in pom.xml:

```xml
<dependency>
    <groupId>io.eventbob</groupId>
    <artifactId>io.eventbob.spring</artifactId>
    <version>${project.version}</version>
</dependency>
```

**Step 2:** Application creates Spring Boot main class and imports EventBobConfig:

```java
@SpringBootApplication
@Import(EventBobConfig.class)
public class MyMicrolithApplication {
  public static void main(String[] args) {
    SpringApplication.run(MyMicrolithApplication.class, args);
  }
  
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("handler1.jar"),
        Paths.get("handler2.jar")
    );
  }
}
```

**Step 3:** Application runs. EventBobConfig receives injected JAR paths, loads handlers, wires EventBob.

---

## Boundary Violations to Avoid

**❌ Never do this:**
- Import `io.eventbob.core.JarHandlerLoader` (it's package-private, you cannot)
- Pass Spring types into core (e.g., passing ApplicationContext to EventHandler)
- Put domain logic in Spring handlers (domain logic belongs in core)
- Make core depend on Spring (dependency must point inward)
- Hard-code configuration in this library (applications provide configuration)

**✅ Correct patterns:**
- Use `HandlerLoader.jarLoader()` factory method
- Convert Spring types to core types at the boundary (e.g., HTTP request → Event)
- Keep Spring handlers thin (adapters only, no business logic)
- Spring depends on core interfaces, core defines contracts
- Accept configuration via constructor injection

---

## Dependencies

**Internal:**
- `io.eventbob.core` (domain abstractions)

**External:**
- Spring Boot (server, dependency injection)
- Spring Web (HTTP transport)
- SLF4J (logging)

**Why these dependencies?**
- Spring Boot: Infrastructure framework (web server, DI container)
- SLF4J: Infrastructure concern (logging)
- These are infrastructure choices. Core remains independent.

---

## Testing Strategy

**Unit tests:**
- Test handlers in isolation (mock Event inputs, assert Event outputs)
- Test configuration (verify beans are wired correctly with injected paths)

**Integration tests:**
- Test full EventBob wiring (load JARs, route events, verify responses)
- Test HTTP transport (send HTTP requests, verify responses)

**What NOT to test here:**
- HandlerLoader behavior (tested in core module)
- Event routing logic (tested in core module)
- Test only infrastructure-specific concerns

---

## Architectural Invariants (Must Never Violate)

1. **Dependency direction** - Spring → Core, never Core → Spring
2. **No framework leakage** - Spring types never cross into core
3. **Thin adapters** - No business logic in infrastructure layer
4. **Public API only** - Spring depends only on core's public interfaces
5. **Library status** - No main class, no hard-coded configuration

**Enforcement:** Maven module boundaries provide structural enforcement. Automated tests planned.

---

## Evolution Path

### Current State
- Constructor injection for JAR paths
- REST endpoint adapter (POST /events)
- HealthcheckHandler built-in
- Library module (no main class)

### Future Enhancements

**Configuration evolution:**
- Accept configuration object (not just List<Path>)
- Configuration object may include:
  - JAR paths
  - Remote capability routing (which capabilities are remote)
  - Service discovery endpoints
  - Metrics/observability settings

**Transport evolution:**
- Additional adapters (gRPC, WebSocket, message queues)
- All adapters follow same pattern: protocol → Event → EventBob → Event → protocol

**Module structure (when multiple transports exist):**
```
io.eventbob.spring
├── config/
│   └── EventBobConfig
├── adapters/
│   ├── HttpEventAdapter
│   ├── GrpcEventAdapter (future)
│   └── WebSocketEventAdapter (future)
└── handlers/
    └── HealthcheckHandler
```

Each adapter converts its protocol to Event, routes through EventBob, converts Event back to protocol.

---

## Example Microlith Applications

See `io.eventbob.example.microlith.spring.echo` for a working example of how to use this library.

Other microlith applications follow the same pattern:
- Import io.eventbob.spring
- Provide @Bean for List<Path> handlerJarPaths
- Create Spring Boot main class

The library handles the rest.
