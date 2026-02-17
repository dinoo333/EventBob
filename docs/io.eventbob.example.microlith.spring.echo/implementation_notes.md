# io.eventbob.example.microlith.spring.echo Implementation Notes

## Module Purpose

This module demonstrates the **microlith pattern** - a concrete Spring Boot application that imports the `io.eventbob.spring` library and specifies which handler JARs to load. This represents a single deployable EventBob server with a specific set of capabilities.

## What is a Microlith?

A **microlith** is a concrete EventBob deployment that:
- Runs as a standalone Spring Boot application
- Loads a specific set of handler JARs (capabilities)
- Exposes those capabilities via HTTP REST endpoints
- Can invoke capabilities on other microliths (future enhancement)

Unlike a monolith (where all code is compiled together) or microservices (where each capability is a separate deployment), a microlith:
- Dynamically loads capabilities from JARs at startup
- Provides coarse-grained deployability (multiple related capabilities per server)
- Enables flexible capability distribution across servers
- Supports both local and remote capability invocation

## Module Patterns

### Application Pattern

**Applied in:** EchoApplication

**Pattern:**
- Standard `@SpringBootApplication` entry point with `main()` method
- Imports `EventBobConfig` from `io.eventbob.spring` library via `@Import`
- Uses `@ComponentScan` to discover Spring components
- Provides configuration via `@Bean` methods

**Structure:**
```java
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.echo"})
@Import(EventBobConfig.class)
public class EchoApplication {
  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
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

### Handler JAR Configuration Pattern

**Applied in:** handlerJarPaths() bean method

**Pattern:**
- Concrete application provides `List<Path>` bean
- Spring injects this bean into `EventBobConfig` constructor
- JAR paths are specific to this microlith's capabilities
- Paths are relative to project root (development convenience)

**This microlith's capabilities:**
- `echo` - Returns input text unchanged
- `lower` - Converts input text to lowercase

**Design decisions:**
- Relative paths for development (no need to reconfigure for local testing)
- Explicit list (clear documentation of what this microlith provides)
- Compile-time dependencies on handler modules (ensures JARs are built before application starts)

**Future evolution:**
- Externalize to `application.properties` for production deployment
- Support absolute paths or environment variable substitution
- Support classpath-relative paths for containerized deployments

### Maven Dependency Pattern

**Applied in:** pom.xml

**Pattern:**
- Depends on `io.eventbob.spring` (compile scope) - provides Spring Boot integration
- Depends on handler modules (runtime scope) - ensures handler JARs are built
- Spring Boot Maven plugin configured for executable JAR

**Dependency structure:**
```
io.eventbob.example.microlith.spring.echo (application)
  ├─ io.eventbob.spring (compile) - Spring Boot integration library
  ├─ io.eventbob.example.echo (runtime) - Handler JAR dependency
  └─ io.eventbob.example.lower (runtime) - Handler JAR dependency
```

**Why runtime scope for handlers?**
- Handlers are loaded dynamically from JARs (not compiled into application)
- Runtime scope ensures Maven builds them before building application
- Prevents accidental compile-time coupling to handler classes
- JAR files are referenced by file path, not classpath

## Configuration

### application.properties

**Location:** `src/main/resources/application.properties`

**Current configuration:**
```properties
server.port=8080
```

**Purpose:**
- Standard Spring Boot server configuration
- No EventBob-specific configuration needed (JAR paths in Java config)

**Future enhancements:**
- Externalize handler JAR paths
- Remote microlith endpoint configuration
- Logging configuration

## Testing

### Manual Testing

Start the application and test via HTTP:

```bash
# Start server
mvn spring-boot:run

# Test echo capability
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"target":"echo","payload":"hello world"}'

# Test lower capability
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"target":"lower","payload":"HELLO WORLD"}'

# Test healthcheck
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"target":"healthcheck"}'
```

### Automated Testing

- No automated tests yet (application is configuration, not logic)
- Future: Integration tests that start application and verify capabilities

## Code Quality

### Current State
- Minimal configuration-only application
- No business logic (all logic in handlers and library)
- Clean baseline (no technical debt)
- Single responsibility: wire up echo+lower microlith

### Design Principles Applied

**YAGNI:**
- No speculative configuration for remote invocation (not needed yet)
- No complex path resolution (simple relative paths work for now)
- No configuration file parsing (Java config is sufficient)

**Clarity:**
- Bean method name `handlerJarPaths()` clearly states what it provides
- List of paths explicitly documents this microlith's capabilities
- No hidden configuration or magic conventions

**Restraint:**
- Only one class (EchoApplication)
- Only one bean method (handlerJarPaths)
- No unnecessary abstractions or patterns

## Usage Pattern

To create a similar microlith with different capabilities:

1. Copy this module structure
2. Rename package and application class
3. Update `handlerJarPaths()` bean to return different JAR paths
4. Update `pom.xml` dependencies to reference different handler modules
5. Optionally change server port in application.properties

Example - creating a microlith with `upper` capability:
```java
@Bean
public List<Path> handlerJarPaths() {
  return List.of(
    Paths.get("io.eventbob.example.upper/target/io.eventbob.example.upper-1.0.0-SNAPSHOT.jar")
  );
}
```

## Future Enhancements

### Remote Invocation (Planned)

When implementing remote microlith-to-microlith invocation:

1. Add `Map<String, URI>` bean for remote capability routing:
   ```java
   @Bean
   public Map<String, URI> remoteCapabilities() {
     return Map.of(
       "upper", URI.create("http://localhost:8081/events")
     );
   }
   ```

2. Library will use this to route:
   - If capability in `remoteCapabilities` map → HTTP POST to remote microlith
   - Else → load from local JAR and execute locally

3. This enables distributed capability deployment while maintaining single EventBob API

### Configuration Externalization (Planned)

Move JAR paths to application.properties:
```properties
eventbob.handler.jars=io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar,\
                      io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar
```

Requires:
- Update `EventBobConfig` to accept optional String property
- Parse comma-separated list into `List<Path>`
- Keep `List<Path>` bean as fallback for Java-based configuration

## Known Limitations

1. **Working directory dependency:** JAR paths are relative to project root. Application must be started from correct directory. Production deployment needs absolute paths or classpath-relative resolution.

2. **No service discovery:** Remote capability endpoints (future) will be hard-coded. No dynamic discovery, load balancing, or failover.

3. **No capability validation:** Application starts successfully even if handler JARs are missing or malformed (fails at first invocation). Should fail fast at startup.

4. **Single server port:** Cannot run multiple microliths on same machine without port configuration. Need better defaults or automatic port assignment for testing.
