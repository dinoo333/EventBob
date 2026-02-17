# io.eventbob.spring Domain Specification

## Module Scope

This module provides Spring Boot infrastructure integration for EventBob server deployment. Responsible for:
- Loading microservices from JAR files at startup
- Discovering EventHandler implementations via @Capability annotations
- Registering handlers with EventBob router
- Exposing macrolith capabilities via HTTP (work-in-progress)

## Ubiquitous Language

This module uses the **same ubiquitous language as core** (see top-level `docs/domain_spec.md`). No new domain concepts are introduced. This is an infrastructure adapter layer.

Key terms from core that apply here:
- **Microservice** - Code packaged in a JAR that integrates via EventHandler implementations
- **EventHandler** - The integration contract microservices implement
- **Capability** - What a handler provides (declared via `@Capability` annotation)
- **HandlerLoader** - Mechanism for loading microservices from JARs
- **Event** - Message envelope for communication
- **EventBob** - Event router
- **Dispatcher** - Facility for sending events to other handlers

## Bounded Context

io.eventbob.spring is **not a separate bounded context**. It is infrastructure implementation within the EventBob bounded context.

## Current Implementation (EventBobConfig)

### Microservice Loading at Startup

**Current behavior:**
- `EventBobConfig` uses `HandlerLoader` to load microservices from hard-coded JAR paths at application startup
- Discovers EventHandler implementations annotated with @Capability
- Registers discovered handlers with EventBob router

**Hard-coded JAR paths (TEMPORARY):**
```java
List.of(
    Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
    Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
)
```

**Known limitation:** JAR paths are not configurable. This is temporary implementation for testing. Future work: read JAR paths from configuration (application.yml, environment variables, or command-line arguments).

### Microservice Discovery and Registration

**Process:**
1. `HandlerLoader` loads each JAR with isolated class loader
2. Scans for classes annotated with @Capability
3. Instantiates discovered EventHandler implementations
4. Returns handler instances to EventBobConfig
5. EventBobConfig registers handlers with EventBob router

**Result:** All capabilities from loaded microservices become routable within the macrolith.

## Work-in-Progress

This module is under active development. Implementation patterns and structure are evolving.

### Future Work

1. **Configurable JAR paths** - Read from application.yml or environment variables instead of hard-coding
2. **HTTP transport adapter** - Expose macrolith capabilities via REST endpoints
3. **Service discovery integration** - Register macrolith with service registry (Consul, Eureka, etc.)
4. **Health checks** - Report health of loaded microservices
