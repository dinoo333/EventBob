# io.eventbob.example.macrolith.echo Architecture

## Module Purpose

Concrete EventBob macrolith application that provides "echo" and "lower" capabilities. This is a demonstration macrolith showing how to configure and deploy EventBob with specific handlers.

**This is an application module.** It has a Spring Boot main class, concrete JAR path configuration, and deployment properties. It is built on top of the `io.eventbob.spring` library.

---

## Layer Assignment

This module is in the **application layer** (outermost):
- Depends on `io.eventbob.spring` (infrastructure library)
- Depends on `io.eventbob.example.echo` and `io.eventbob.example.lower` (handler JARs, runtime)
- Provides concrete configuration (which handlers to load)
- Has Spring Boot main class

**Dependency direction:**
```
io.eventbob.example.macrolith.echo  →  io.eventbob.spring  →  io.eventbob.core
(application - concrete)            (infrastructure)          (domain)
```

---

## Key Components

### Spring Boot Application

**EchoMacrolithApplication:**
- Entry point for the macrolith server
- Imports `EventBobConfig` from io.eventbob.spring library
- Provides configuration via `@Bean` methods
- Starts embedded web server (Tomcat)

**Structure:**
```java
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.macrolith.echo"})
@Import(EventBobConfig.class)
public class EchoMacrolithApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoMacrolithApplication.class, args);
  }

  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar"),
        Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar")
    );
  }
}
```

**Key design decisions:**
- `@ComponentScan` includes both io.eventbob.spring (for EventController) and this module
- `@Import(EventBobConfig.class)` brings in the generic EventBob configuration
- `handlerJarPaths()` bean provides concrete JAR paths to EventBobConfig constructor
- JAR paths are relative to project root (for development convenience)

### Configuration

**Handler JAR paths:**
- Specified in `handlerJarPaths()` @Bean method
- Injected into `EventBobConfig` constructor
- Currently hard-coded (suitable for development/testing)

**Future evolution:**
- Read from application.properties (externalized configuration)
- Support JAR directory scanning
- Support absolute paths or classpath resources

**Deployment properties:**
- `application.properties` or `application.yml` in `src/main/resources`
- Server port, logging levels, Spring Boot settings

---

## Capabilities Provided

This macrolith provides two capabilities loaded from JAR files:

### "echo" capability
- **Handler JAR:** io.eventbob.example.echo
- **Behavior:** Returns the input data unchanged
- **Use case:** Testing request/response flow

### "lower" capability
- **Handler JAR:** io.eventbob.example.lower
- **Behavior:** Converts input string to lowercase
- **Use case:** Testing data transformation

### "healthcheck" capability
- **Source:** Built into io.eventbob.spring library
- **Behavior:** Returns health status
- **Use case:** Infrastructure monitoring

---

## Runtime Behavior

### Startup Sequence

1. **Spring Boot initializes**
   - EchoMacrolithApplication.main() invoked
   - SpringApplication.run() starts Spring context

2. **Spring DI wiring**
   - `handlerJarPaths()` bean created (List<Path>)
   - `EventBobConfig` constructor receives handlerJarPaths
   - `EventBobConfig` loads handlers from JARs using HandlerLoader

3. **Handler loading**
   - HandlerLoader scans echo.jar for @Capability classes
   - HandlerLoader scans lower.jar for @Capability classes
   - Handlers instantiated via reflection (no-args constructors)
   - Handlers registered with EventBob by capability name

4. **Server ready**
   - EventController registered at POST /events
   - Embedded Tomcat started on configured port (default 8080)
   - Application ready to process events

### Request Flow

**Client sends HTTP POST to /events:**
```json
{
  "capability": "lower",
  "data": {"text": "HELLO WORLD"}
}
```

**Processing:**
1. EventController receives HTTP request
2. EventDto converted to domain Event
3. Event routed to EventBob.processEvent()
4. EventBob looks up "lower" handler
5. Handler processes event synchronously
6. Response Event returned
7. EventDto converted back from Event
8. HTTP response sent to client

---

## Dependencies

### Maven Dependencies

**Internal (compile):**
- `io.eventbob.spring` - Infrastructure library

**Internal (runtime):**
- `io.eventbob.example.echo` - Echo handler JAR
- `io.eventbob.example.lower` - Lower handler JAR

**External:**
- Spring Boot (transitively via io.eventbob.spring)
- SLF4J (transitively via io.eventbob.spring)

**Dependency graph:**
```
io.eventbob.example.macrolith.echo
  ├─ io.eventbob.spring (compile)
  │   └─ io.eventbob.core (compile)
  ├─ io.eventbob.example.echo (runtime)
  │   └─ io.eventbob.core (compile)
  └─ io.eventbob.example.lower (runtime)
      └─ io.eventbob.core (compile)
```

**Why runtime scope for handler JARs:**
- Handler JARs are loaded dynamically at runtime via URLClassLoader
- Maven runtime scope ensures JARs are built before macrolith runs
- Prevents compile-time coupling between macrolith and handler implementations

---

## Design Principles

### Concrete Configuration

This module makes concrete decisions that the library left abstract:
- **Which handlers:** echo and lower (not upper, not other handlers)
- **Where are JARs:** Relative paths to target/ directories
- **What port:** Configured in application.properties

The library (`io.eventbob.spring`) remains generic. This module makes it specific.

### Deployment Unit

This module represents a single deployable unit:
- One JAR file (built with spring-boot-maven-plugin)
- One process when running
- One logical service (echo-macrolith)
- Multiple capabilities bundled together

This is the "macrolith" concept: multiple capabilities in one deployment.

### Evolution Path

**Current state (hard-coded):**
- JAR paths in @Bean method
- Development convenience (relative paths work)

**Next iteration (externalized):**
- JAR paths in application.properties
- Absolute paths or classpath resources
- Environment-specific configuration (dev/test/prod)

**Future (dynamic):**
- JAR directory scanning
- Hot-reload of new handlers
- Service discovery integration

---

## Testing

### Manual Testing

**Build and run:**
```bash
mvn clean package
cd io.eventbob.example.macrolith.echo
mvn spring-boot:run
```

**Test echo capability:**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"capability":"echo","data":{"text":"hello"}}'
```

**Test lower capability:**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"capability":"lower","data":{"text":"HELLO WORLD"}}'
```

**Test healthcheck:**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"capability":"healthcheck","data":{}}'
```

### Integration Testing

**Recommended approach:**
- Use Spring Boot test support (`@SpringBootTest`)
- Start full application context
- Use TestRestTemplate to send HTTP requests
- Verify responses for all three capabilities
- Test error cases (unknown capability, malformed request)

---

## Relationship to Other Macrolith Applications

This is the **first** concrete macrolith application. More will follow:

**Future macrolith applications:**
- `io.eventbob.example.macrolith.upper` - Loads "upper" handler
- `io.eventbob.example.macrolith.messages` - Loads read/write/delete handlers
- Custom macroliths per deployment scenario

**Pattern:**
Every macrolith application follows the same structure:
1. Depends on `io.eventbob.spring` library
2. Has Spring Boot main class with `@SpringBootApplication`
3. Imports `EventBobConfig`
4. Provides `@Bean` for `List<Path> handlerJarPaths`
5. Specifies which handler JARs to load

The library handles the rest. The application just configures.

---

## Architectural Invariants

1. **Depends on library, not core directly** - Let the library manage core interactions
2. **Configuration only** - No domain logic, no infrastructure logic in this module
3. **One macrolith, multiple capabilities** - Bundle related handlers together
4. **Reuses library completely** - No duplication of EventBobConfig or transport adapters

**What NOT to put here:**
- Custom EventHandler implementations (those go in handler modules)
- Infrastructure code (that's in io.eventbob.spring)
- Domain logic (that's in io.eventbob.core)
- Only configuration and wiring belong here

---

## Open Questions

1. **JAR path resolution:** Should paths be relative, absolute, or classpath resources?
2. **Configuration format:** Hard-coded @Bean, application.properties, or external config file?
3. **Deployment model:** Single instance, replicated, or container orchestration?
4. **Service registration:** How does this macrolith register with service discovery?
5. **Monitoring:** What metrics and health checks should be exposed?

These questions will be answered as requirements clarify. Current hard-coded approach works for development and testing.
