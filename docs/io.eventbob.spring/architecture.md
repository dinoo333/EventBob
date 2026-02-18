# io.eventbob.spring Architecture

## Module Purpose

Spring Boot infrastructure library providing server deployment, handler wiring, and transport adapters for EventBob.

**This is a library module.** It has no main class, no hard-coded configuration. Concrete microlith applications import this library and provide configuration via constructor injection.

**Extended capability:** This module now supports both local handler loading (from JARs) and remote handler loading (from HTTP endpoints), enabling location-transparent handler invocation.

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

## Package Structure

```
io.eventbob.spring
├── EventBobConfig                 (Spring configuration - constructor injection)
├── EventController                (REST endpoint adapter - server-side)
├── EventDto                       (HTTP/Event boundary DTO - anti-corruption layer)
├── adapter/
│   ├── http/
│   │   ├── HttpEventHandlerAdapter (EventHandler → HTTP client adapter)
│   │   └── RemoteEventDto          (Remote HTTP DTO - separate from local EventDto)
│   └── RemoteCapability            (Remote endpoint configuration record)
├── loader/
│   └── RemoteHandlerLoader        (HandlerLoader implementation for remote endpoints)
└── handlers/
    └── HealthcheckHandler         (Built-in system handler)
```

**Package responsibilities:**

| Package | Responsibility | Boundary |
|---------|---------------|----------|
| **Top-level** | Configuration and local REST endpoint (server-side) | Spring ↔ Core |
| **adapter/http/** | HTTP client-side adapters for remote handler invocation | Spring (adapter) ↔ Core (domain) |
| **adapter/** | Remote capability configuration (RemoteCapability record) | Configuration ↔ Adapters |
| **loader/** | HandlerLoader implementations beyond core's JarHandlerLoader | Spring (loader) ↔ Core (HandlerLoader interface) |
| **handlers/** | Built-in system handlers | Spring (implementation) ↔ Core (EventHandler interface) |

**Design rationale:**
- Top-level contains server-side concerns (accepting HTTP requests, Spring configuration)
- `adapter/` contains client-side concerns (making HTTP requests to remote handlers) and configuration records
- `loader/` contains alternative HandlerLoader implementations (remote loading vs. core's JAR loading)
- Clear separation between server role (EventController, EventDto) and client role (HttpEventHandlerAdapter, RemoteEventDto)

---

## Key Components

### Configuration

**EventBobConfig:**
- Spring dependency injection wiring
- EventBob instance configuration with handlers
- **Constructor injection:** Accepts `List<Path> handlerJarPaths` and `List<RemoteCapability> remoteCapabilities` from application layer
- **Supports both local and remote handlers**

**Constructor injection pattern:**
```java
@Configuration
public class EventBobConfig {
  private final List<Path> handlerJarPaths;
  private final List<RemoteCapability> remoteCapabilities;

  /**
   * Create EventBob configuration with specified handler JAR paths and remote capabilities.
   * Applications must provide:
   * - List<Path> handlerJarPaths bean (required, may be empty if all handlers are remote)
   * - List<RemoteCapability> remoteCapabilities bean (optional, may be empty if all handlers are local)
   */
  public EventBobConfig(
      List<Path> handlerJarPaths,
      @Autowired(required = false) List<RemoteCapability> remoteCapabilities) {
    this.handlerJarPaths = handlerJarPaths;
    this.remoteCapabilities = remoteCapabilities != null ? remoteCapabilities : List.of();
  }

  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler, HttpClient httpClient) {
    EventBob.Builder builder = EventBob.builder();
    builder.handler("healthcheck", healthcheckHandler);
    
    // Load and register all handlers (JAR-based and remote)
    Map<String, EventHandler> allHandlers = loadAllHandlers(httpClient);
    allHandlers.forEach(builder::handler);
    
    return builder.build();
  }

  private Map<String, EventHandler> loadAllHandlers(HttpClient httpClient) throws IOException {
    Map<String, EventHandler> allHandlers = new HashMap<>();
    
    // Load JAR-based handlers
    HandlerLoader jarLoader = HandlerLoader.jarLoader(handlerJarPaths);
    Map<String, EventHandler> jarHandlers = jarLoader.loadHandlers();
    allHandlers.putAll(jarHandlers);
    
    // Load remote handlers
    HandlerLoader remoteLoader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> remoteHandlers = remoteLoader.loadHandlers();
    
    // Check for duplicates before merging
    for (String capability : remoteHandlers.keySet()) {
      if (allHandlers.containsKey(capability)) {
        throw new IllegalStateException(
          "Duplicate capability '" + capability + "' found in both JAR and remote handlers");
      }
    }
    
    allHandlers.putAll(remoteHandlers);
    return allHandlers;
  }
}
```

**RemoteCapability record:**
```java
/**
 * Represents a remote capability endpoint for inter-microlith communication.
 * Maps a capability name (e.g., "upper", "email") to a remote endpoint URI.
 */
public record RemoteCapability(String name, URI uri) {
  public RemoteCapability {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Capability name must not be null or blank");
    }
    if (uri == null) {
      throw new IllegalArgumentException("URI must not be null");
    }
  }
}
```

**Key design decisions:**
1. JAR paths and remote capabilities are injected via constructor, not hard-coded
2. `handlerJarPaths` is required; `remoteCapabilities` is optional (`@Autowired(required = false)`)
3. A microlith can have:
   - Only local handlers (JAR paths provided, no remote capabilities)
   - Only remote handlers (remote capabilities provided, empty JAR paths list)
   - Hybrid (both local and remote handlers)
4. The library is reusable across different microlith deployment strategies
5. RemoteCapability uses URI (not String) for type safety and validation

**HandlerLoader dependency:**
Spring depends only on `HandlerLoader` interface (public API) for JAR loading. For remote loading, Spring provides its own `RemoteHandlerLoader` implementation that also implements the `HandlerLoader` interface. This maintains dependency inversion: core defines the contract, infrastructure provides implementations.

### Handlers

**HealthcheckHandler:**
- Built-in system handler
- Registered with capability name "healthcheck"
- Implements EventHandler interface from core
- Returns system health status

**Design rationale:** Healthcheck is infrastructure-specific (checks Spring context health, JVM metrics, etc.), so it lives in infrastructure layer, not core.

### Transport Adapters

#### Server-Side (Accepting Requests)

**EventController:**
- REST endpoint adapter
- Bridges HTTP requests to Event model
- Routes to EventBob
- Converts Event responses back to HTTP

**EventDto:**
- HTTP/Event boundary DTO (server-side)
- Isolates HTTP representation from domain Event
- Prevents Jackson annotations from leaking into core
- **Anti-corruption layer:** Protects domain Event from HTTP framework concerns

**Pattern:**
```java
@RestController
public class EventController {
    private final EventBob eventBob;
    
    @PostMapping("/events")
    public ResponseEntity<EventDto> handleEvent(@RequestBody EventDto request) {
        Event event = request.toEvent();  // HTTP → Domain
        CompletableFuture<Event> response = eventBob.processEvent(event);
        return ResponseEntity.ok(EventDto.fromEvent(response.get()));  // Domain → HTTP
    }
}
```

#### Client-Side (Making Requests)

**HttpEventHandlerAdapter:**
- Implements EventHandler interface from core
- Encapsulates HTTP client logic (HttpClient, error handling, serialization)
- Converts Event → RemoteEventDto → HTTP POST
- Converts HTTP response → RemoteEventDto → Event
- **Location transparency:** Domain code sees EventHandler, not HTTP

**RemoteEventDto:**
- HTTP/Event boundary DTO (client-side)
- Separate from EventDto (server-side) to allow independent evolution
- Prevents HTTP client types from leaking into domain
- **Anti-corruption layer:** Protects domain Event from HTTP client concerns

**Pattern:**
```java
public class HttpEventHandlerAdapter implements EventHandler {
    private final URI remoteEndpoint;
    private final HttpClient httpClient;
    
    @Override
    public Event handle(Event event) {
        RemoteEventDto requestDto = RemoteEventDto.fromEvent(event);  // Domain → HTTP
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(remoteEndpoint.resolve("/events"))
            .POST(HttpRequest.BodyPublishers.ofString(toJson(requestDto)))
            .header("Content-Type", "application/json")
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        RemoteEventDto responseDto = fromJson(response.body());
        
        return responseDto.toEvent();  // HTTP → Domain
    }
}
```

**Key insight:** HttpEventHandlerAdapter is an adapter in the true architectural sense:
- It adapts external HTTP protocol to internal EventHandler interface
- It lives in infrastructure layer (where HTTP concerns belong)
- Domain code (EventBob router) sees only EventHandler interface
- HTTP complexity is encapsulated and hidden from domain

### Remote Handler Loading

**RemoteHandlerLoader:**
- Implements HandlerLoader interface from core
- Creates HttpEventHandlerAdapter instances for remote capabilities
- Returns Map<String, EventHandler> where values are HTTP adapters

**Pattern:**
```java
public class RemoteHandlerLoader implements HandlerLoader {
    private final List<RemoteCapability> remoteCapabilities;
    private final HttpClient httpClient;
    
    public RemoteHandlerLoader(List<RemoteCapability> remoteCapabilities, HttpClient httpClient) {
        this.remoteCapabilities = remoteCapabilities;
        this.httpClient = httpClient;
    }
    
    @Override
    public Map<String, EventHandler> loadHandlers() {
        Map<String, EventHandler> handlers = new HashMap<>();
        for (RemoteCapability capability : remoteCapabilities) {
            HttpEventHandlerAdapter adapter = new HttpEventHandlerAdapter(
                capability.uri(), 
                capability.name(),
                httpClient
            );
            handlers.put(capability.name(), adapter);
        }
        return handlers;
    }
}
```

**Key insight:** RemoteHandlerLoader follows the same contract as core's JarHandlerLoader:
- Implements HandlerLoader interface
- Returns Map<String, EventHandler>
- EventBob router treats both loader types identically

---

## Design Principles

### Location Transparency

**Goal:** Domain code should be agnostic to handler location.

**Implementation:**
1. All handlers implement EventHandler interface (local and remote)
2. EventBob router depends only on EventHandler interface
3. HttpEventHandlerAdapter encapsulates remote invocation complexity
4. Client code cannot distinguish local from remote handlers

**Example:**
```java
// Domain code (same for local or remote handler)
Event event = Event.builder()
    .capability("upper")
    .payload("hello")
    .build();

CompletableFuture<Event> response = eventBob.processEvent(event);
// "upper" may be local (from JAR) or remote (via HTTP) — domain code doesn't know or care
```

### Bridge Pattern

Infrastructure depends on core abstractions:
- EventHandler (interface)
- Event (domain model)
- EventBob (router)
- HandlerLoader (loading abstraction)

Core remains framework-agnostic. Spring types (HttpClient, ResponseEntity, Jackson annotations) stay in this module, never leak into core.

### Adapter Pattern

**Server-side adapter:** EventController adapts HTTP protocol to EventBob router
- HTTP request → EventDto → Event → EventBob

**Client-side adapter:** HttpEventHandlerAdapter adapts EventBob router to HTTP protocol
- Event → RemoteEventDto → HTTP request → remote EventBob

Both adapters use DTOs as anti-corruption layers to prevent framework types from crossing boundaries.

### Dependency Inversion

**Traditional (violated):**
```
Spring Config → JarHandlerLoader (concrete class)
```

**Actual (DIP compliant):**
```
Spring Config → HandlerLoader (interface) ← JarHandlerLoader (hidden in core)
                                           ← RemoteHandlerLoader (in Spring)
```

Core defines the HandlerLoader contract. Core provides JarHandlerLoader (package-private). Spring provides RemoteHandlerLoader (public in Spring). Both are hidden behind the interface from the perspective of EventBobConfig.

### Anti-Corruption Layer

**Why two DTOs?**
- **EventDto (server-side):** Adapts incoming HTTP requests to domain Event
- **RemoteEventDto (client-side):** Adapts outgoing HTTP requests to domain Event

Separate DTOs allow:
- Independent evolution (server and client may have different needs)
- Clear separation of concerns (server vs. client)
- Explicit boundary definition (what crosses the wire vs. what stays internal)

**Both DTOs prevent framework types from leaking into core:**
- Jackson annotations stay in DTOs, not in domain Event
- HttpClient types stay in HttpEventHandlerAdapter, not in domain
- Domain Event remains pure (no Spring, no Jackson, no HTTP)

### Library Module Pattern

This module is a **library**, not an application:
- No `main()` method
- No hard-coded configuration
- No Spring Boot Application class
- No `spring-boot-maven-plugin` in pom.xml

Applications import this library and provide:
- Spring Boot main class (`@SpringBootApplication`)
- Configuration beans (`List<Path> handlerJarPaths`, `List<RemoteCapability> remoteCapabilities`)
- Deployment properties (`application.properties`)

---

## How Applications Use This Library

### Local Handlers Only

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

### Remote Handlers Only

```java
@SpringBootApplication
@Import(EventBobConfig.class)
public class RemoteOnlyMicrolithApplication {
  public static void main(String[] args) {
    SpringApplication.run(RemoteOnlyMicrolithApplication.class, args);
  }
  
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of();  // Empty - no local handlers
  }
  
  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://remote-server:8080")),
        new RemoteCapability("archive", URI.create("http://archive-service:9090"))
    );
  }
}
```

### Hybrid (Local + Remote)

```java
@SpringBootApplication
@Import(EventBobConfig.class)
public class HybridMicrolithApplication {
  public static void main(String[] args) {
    SpringApplication.run(HybridMicrolithApplication.class, args);
  }
  
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("echo.jar"), 
        Paths.get("lower.jar")
    );
  }
  
  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://remote-server:8080"))
    );
  }
}
```

**Result:** EventBob router has capabilities "echo", "lower" (local), and "upper" (remote). Domain code treats all three identically.

---

## Location Transparency Architecture

### Design Goal

Domain code should be agnostic to handler location. Whether a handler runs in the same JVM (loaded from JAR) or in a remote process (accessed via HTTP) is a deployment decision, not a domain concern.

### Structural Components

```
┌────────────────────────────────────────────────────────────┐
│  Domain Layer (io.eventbob.core)                          │
│                                                            │
│  EventBob Router                                           │
│       ↓ depends on                                         │
│  EventHandler (interface) ←─────────────┐                 │
│       ↑ implements          implements ↑                   │
└───────┼──────────────────────────────────┼─────────────────┘
        │                                  │
┌───────┼──────────────────────────────────┼─────────────────┐
│       │  Infrastructure Layer (io.eventbob.spring)         │
│       │                                  │                 │
│  EchoHandler                  HttpEventHandlerAdapter      │
│  (from echo.jar)                   ↓ encapsulates         │
│                              HttpClient, RemoteEventDto    │
└────────────────────────────────────────────────────────────┘
```

**Key insight:** EventBob router sees two EventHandler implementations:
1. EchoHandler (from JAR, in-process)
2. HttpEventHandlerAdapter (proxy to remote handler, HTTP)

Both implement the same interface. The router cannot tell the difference. Location is transparent.

### Remote Handler Loading Sequence

```
1. Application configures remote capabilities
   @Bean List<RemoteCapability> remoteCapabilities()
   
2. EventBobConfig receives configuration
   remoteCapabilities = [RemoteCapability("upper", URI.create("http://remote:8080"))]
   
3. EventBobConfig calls loadAllHandlers(httpClient)
   RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient)
   
4. RemoteHandlerLoader creates adapters
   for each RemoteCapability:
     HttpEventHandlerAdapter adapter = new HttpEventHandlerAdapter(uri, name, httpClient)
   
5. Adapters registered with EventBob router
   builder.handler("upper", adapter)
   
6. Client code calls eventBob.processEvent(event)
   Event has capability "upper"
   
7. EventBob routes to HttpEventHandlerAdapter
   Calls adapter.handle(event) — transparent, just like any EventHandler
   
8. HttpEventHandlerAdapter converts and invokes
   Event → RemoteEventDto → HTTP POST to http://remote:8080/events
   
9. Remote server responds
   RemoteEventDto → Event
   
10. Client receives Event
    Indistinguishable from local handler response
```

**Key insight:** Steps 6-10 are identical whether the handler is local or remote. The adapter encapsulates the difference.

---

## Boundary Violations to Avoid

**❌ Never do this:**
- Import `io.eventbob.core.JarHandlerLoader` (it's package-private, you cannot)
- Pass Spring types into core (e.g., passing HttpClient to EventHandler)
- Put domain logic in Spring handlers (domain logic belongs in core)
- Make core depend on Spring (dependency must point inward)
- Hard-code configuration in this library (applications provide configuration)
- Use EventDto in HttpEventHandlerAdapter or RemoteEventDto in EventController (each side has its own DTO)
- Let Jackson annotations leak into domain Event (keep them in DTOs)

**✅ Correct patterns:**
- Use `HandlerLoader.jarLoader(Collection<Path>)` factory method for local handlers
- Use `RemoteHandlerLoader` for remote handlers (both implement HandlerLoader)
- Convert Spring types to core types at the boundary (HTTP → Event via DTO)
- Keep Spring handlers and adapters thin (protocol translation only, no business logic)
- Spring depends on core interfaces, core defines contracts
- Accept configuration via constructor injection (`@Autowired`)
- Use EventDto for server-side, RemoteEventDto for client-side (separate concerns)

---

## Dependencies

**Internal:**
- `io.eventbob.core` (domain abstractions)

**External:**
- Spring Boot (server, dependency injection)
- Spring Web (HTTP transport — server only; client uses java.net.http.HttpClient)
- Jackson (JSON serialization for DTOs, not domain Event)
- SLF4J (logging)

**Why these dependencies?**
- Spring Boot: Infrastructure framework (web server, DI container)
- Spring Web: HTTP server (EventController)
- java.net.http.HttpClient: HTTP client (HttpEventHandlerAdapter) - standard Java 11+ library
- Jackson: JSON serialization at boundaries (EventDto, RemoteEventDto)
- SLF4J: Infrastructure concern (logging)
- These are infrastructure choices. Core remains independent.

---

## Testing Strategy

**Unit tests:**
- Test handlers in isolation (mock Event inputs, assert Event outputs)
- Test configuration (verify beans are wired correctly with injected paths and capabilities)
- Test HttpEventHandlerAdapter (mock HttpClient, verify Event → RemoteEventDto → HTTP)
- Test RemoteHandlerLoader (verify it creates correct adapters from RemoteCapability list)

**Integration tests:**
- Test full EventBob wiring with local handlers (load JARs, route events, verify responses)
- Test full EventBob wiring with remote handlers (mock remote server, verify HTTP calls, verify responses)
- Test hybrid configurations (mix local and remote handlers)
- Test HTTP transport end-to-end (send HTTP requests to EventController, verify responses)

**What NOT to test here:**
- HandlerLoader.jarLoader() behavior (tested in core module)
- Event routing logic (tested in core module)
- Test only infrastructure-specific concerns (Spring wiring, HTTP protocol, DTO conversions)

---

## Architectural Invariants (Must Never Violate)

1. **Dependency direction** - Spring → Core, never Core → Spring
2. **No framework leakage** - Spring types never cross into core
3. **Thin adapters** - No business logic in infrastructure layer (EventController, HttpEventHandlerAdapter are pure adapters)
4. **Public API only** - Spring depends only on core's public interfaces (EventHandler, HandlerLoader, Event)
5. **Library status** - No main class, no hard-coded configuration
6. **DTO boundaries** - EventDto and RemoteEventDto stay in infrastructure, domain Event stays in core
7. **Location transparency** - Domain code cannot distinguish local from remote handlers
8. **Anti-corruption layers** - DTOs prevent HTTP types (Jackson, HttpClient) from leaking into domain

**Enforcement:** Maven module boundaries provide structural enforcement. Automated tests planned.

---

## Evolution Path

### Current State
- Constructor injection for JAR paths and remote capabilities (List<RemoteCapability>)
- REST endpoint adapter (POST /events) for server-side
- HttpEventHandlerAdapter for client-side remote invocation
- RemoteHandlerLoader for remote handler loading
- RemoteCapability record for type-safe endpoint configuration
- Location-transparent handler routing
- HealthcheckHandler built-in
- Library module (no main class)

### Recent Additions
- **HttpEventHandlerAdapter:** Enables remote handler invocation while maintaining EventHandler interface
- **RemoteHandlerLoader:** Implements HandlerLoader for remote endpoint configuration
- **RemoteCapability record:** Type-safe configuration with URI validation
- **RemoteEventDto:** Anti-corruption layer for client-side HTTP communication
- **Package structure:** `adapter/http/` and `loader/` packages separate concerns

### Future Enhancements

**Configuration evolution:**
- RemoteCapability could be extended to include:
  - Health check endpoints
  - Circuit breaker settings
  - Retry policies
  - Service discovery metadata
  - Metrics/observability settings

**Transport evolution:**
- Additional adapters (gRPC, WebSocket, message queues)
- All adapters follow same pattern: protocol → Event → EventBob → Event → protocol

**Module structure (when multiple transports exist):**
```
io.eventbob.spring
├── config/
│   └── EventBobConfig
├── adapter/
│   ├── http/
│   │   ├── HttpEventHandlerAdapter      (client)
│   │   └── RemoteEventDto
│   ├── grpc/
│   │   ├── GrpcEventHandlerAdapter      (future, client)
│   │   └── GrpcEventDto
│   └── websocket/
│       ├── WebSocketEventHandlerAdapter (future, client)
│       └── WebSocketEventDto
├── loader/
│   ├── RemoteHandlerLoader              (HTTP)
│   ├── GrpcHandlerLoader                (future)
│   └── WebSocketHandlerLoader           (future)
├── controller/
│   ├── EventController                  (HTTP server)
│   ├── GrpcEventService                 (future, server)
│   └── WebSocketEventEndpoint           (future, server)
└── handlers/
    └── HealthcheckHandler
```

Each adapter converts its protocol to Event, routes through EventBob, converts Event back to protocol. Server-side and client-side adapters are separated (`controller/` vs. `adapter/`).

---

## Example Microlith Applications

See `io.eventbob.example.microlith.spring.echo` for a working example of how to use this library with local handlers.

Other microlith applications follow the same pattern:
- Import io.eventbob.spring
- Provide @Bean for List<Path> handlerJarPaths (for local handlers)
- Provide @Bean for List<RemoteCapability> remoteCapabilities (for remote handlers)
- Create Spring Boot main class

The library handles the rest. Domain code is agnostic to handler locations.
