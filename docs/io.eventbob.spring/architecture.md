# io.eventbob.spring Architecture

## Module Purpose

Spring Boot infrastructure layer providing server deployment, handler wiring, and transport adapters for EventBob.

---

## Layer Assignment

This module is in the **infrastructure layer** (outermost):
- Depends on `io.eventbob.core` (domain abstractions)
- Provides framework-specific implementations
- Core never depends on this module (dependency inversion)

**Dependency direction:**
```
io.eventbob.spring → io.eventbob.core
(infrastructure)    (domain)
```

---

## Key Components

### Application Bootstrap

**EventBobApplication:**
- Spring Boot application entry point
- Server startup and lifecycle management
- Launches embedded servlet container (Tomcat/Jetty)

### Configuration

**EventBobConfig:**
- Spring dependency injection wiring
- EventBob instance configuration with handlers
- JAR path configuration (currently hard-coded)

**HandlerLoader dependency:**
```java
@Configuration
public class EventBobConfig {
    private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();  // Factory method
        
        List<Path> jarPaths = List.of(
            Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
            Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
        );
        
        return loader.loadHandlers(jarPaths);
    }
    
    @Bean
    public EventBob eventBob(HealthcheckHandler healthcheckHandler) {
        EventBob.Builder builder = EventBob.builder();
        builder.handler("healthcheck", healthcheckHandler);
        
        Map<String, EventHandler> handlers = loadHandlersFromJars();
        handlers.forEach(builder::handler);
        
        return builder.build();
    }
}
```

**Key insight:** Spring depends only on `HandlerLoader` interface (public API), never on `JarHandlerLoader` (package-private implementation). This is Dependency Inversion Principle in action.

### Handlers

**HealthcheckHandler:**
- Built-in system handler
- Registered with capability name "healthcheck"
- Implements EventHandler interface from core
- Returns system health status

**Design rationale:** Healthcheck is infrastructure-specific (checks Spring context health, JVM metrics, etc.), so it lives in infrastructure layer, not core.

### Transport Adapters

**Status:** Work-in-progress

**Future:** HTTP adapter will bridge HTTP requests to Event model, route to EventBob, and convert Event responses back to HTTP.

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

### JAR Path Configuration

**Current state:** Hard-coded paths to example JARs

**Future evolution:**
- Configuration file (application.yml) with JAR paths
- JAR directory scanning
- Dynamic JAR reloading (hot deployment)

**Restraint:** Configuration mechanism deferred until requirements clarify. Hard-coded paths work for current testing needs.

---

## Boundary Violations to Avoid

**❌ Never do this:**
- Import `io.eventbob.core.JarHandlerLoader` (it's package-private, you cannot)
- Pass Spring types into core (e.g., passing ApplicationContext to EventHandler)
- Put domain logic in Spring handlers (domain logic belongs in core)
- Make core depend on Spring (dependency must point inward)

**✅ Correct patterns:**
- Use `HandlerLoader.jarLoader()` factory method
- Convert Spring types to core types at the boundary (e.g., HTTP request → Event)
- Keep Spring handlers thin (adapters only, no business logic)
- Spring depends on core interfaces, core defines contracts

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
- Test configuration (verify beans are wired correctly)

**Integration tests:**
- Test full EventBob wiring (load JARs, route events, verify responses)
- Test HTTP transport (send HTTP requests, verify responses)

**What NOT to test here:**
- HandlerLoader behavior (tested in core module)
- Event routing logic (tested in core module)
- Test only infrastructure-specific concerns

---

## Future Evolution

### When HTTP Adapter Is Added

**HttpEventAdapter:**
- Converts HttpServletRequest → Event
- Routes Event through EventBob
- Converts Event response → HttpServletResponse

**Boundary conversion:**
```java
@RestController
public class HttpEventAdapter {
    private final EventBob eventBob;
    
    @PostMapping("/{capability}")
    public ResponseEntity<Map<String, Object>> handleEvent(
        @PathVariable String capability,
        @RequestBody Map<String, Object> body,
        HttpServletRequest request
    ) {
        Event event = buildEventFromHttp(capability, body, request);
        CompletableFuture<Event> response = eventBob.processEvent(event);
        return buildHttpResponseFromEvent(response.get());
    }
}
```

### When Configuration Becomes Dynamic

**application.yml:**
```yaml
eventbob:
  handlers:
    jarDirectory: /opt/eventbob/handlers
    scanInterval: 30s
```

**EventBobConfig evolution:**
- Read JAR paths from configuration
- Watch directory for new JARs
- Reload handlers dynamically

### When Multiple Transports Are Needed

**Module structure:**
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

## Architectural Invariants (Must Never Violate)

1. **Dependency direction** - Spring → Core, never Core → Spring
2. **No framework leakage** - Spring types never cross into core
3. **Thin adapters** - No business logic in infrastructure layer
4. **Public API only** - Spring depends only on core's public interfaces

**Enforcement:** ArchUnit tests verify these invariants on every build.

---

## Work-in-Progress

This module is under active development. Components and structure are evolving as requirements clarify.

**Next steps:**
- Implement HTTP adapter (convert HTTP ↔ Event)
- Make JAR paths configurable (application.yml)
- Add observability (metrics, tracing)
