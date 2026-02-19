# EventBob Domain Specification

## What EventBob Is

EventBob is a **framework for bundling multiple microservices into a microlith** to solve the chatty-application anti-pattern.

### The Problem It Solves

When too many microservices make excessive network calls to each other:
- Network becomes saturated
- Performance is dismal
- Engineers "did a shit job at architecting a system"

**First rule of distributed systems:** Don't distribute if you don't have to. This rule gets broken often. EventBob fixes the mess.

### How It Works

1. EventBob server loads multiple JARs containing microservice code
2. Microservices integrate with EventBob by implementing EventHandler interface(s) annotated with @Capability
3. EventBob discovers handlers and routes events to them in-process (fast, no network overhead)
4. Outside: the microlith exposes all bundled capabilities as a single monolithic service

**Key insight:** EventBob is still a server acting as a microservice. Just bigger and less chatty.

### Architecture Compliance

EventBob remains **compliant with microservice architecture**. It just allows engineers to bundle things together in a more performant way—fewer network calls, faster execution.

---

## Core Domain Principles

### Location Transparency

Handlers can be local (loaded from JARs in-process) or remote (accessed via HTTP/gRPC). EventBob's routing layer treats both identically—the location is an implementation detail hidden behind the EventHandler interface.

**Implication:** A capability can be relocated from one microlith to another without changing the routing logic. Only configuration changes (which HandlerLoader implementation to use).

**Vocabulary:** We say "capability" not "local capability" or "remote capability" in the routing layer. Location is infrastructure concern, not domain concern.

---

## Ubiquitous Language

### Microlith
A deployment unit containing multiple microservice capabilities bundled together. A microlith is a single server process that loads multiple JARs and exposes their combined functionality.


**Example:** The "messages-service" microlith bundles message-reading, message-writing, and message-deletion capabilities into one process.

### Microservice
Code packaged in a JAR that integrates with EventBob by implementing EventHandler interface(s). Each microservice is independently developed and versioned.

**Deployment:** Microservices are packaged as JARs. EventBob loads these JARs at startup.

**Integration contract:** Microservices must implement at least one EventHandler annotated with @Capability to be discoverable.

### EventHandler
The integration contract that microservices must implement to integrate with EventBob. Single method: `Event handle(Event, Dispatcher)`.

**Discovery:** EventBob discovers EventHandler implementations via @Capability annotations.

**Example:** A message-reading microservice provides `MessagesReadHandler implements EventHandler`.

### Capability
What a microservice provides. A capability is declared via the @Capability annotation on an EventHandler implementation.

**Current annotation structure:**
- value: String - the capability identifier (e.g., "get-message-content", "create-message")
- version: int - version of the capability contract (default: 1)

**Declaration:** Microservices declare capabilities via @Capability annotation on EventHandler implementations. A single handler may declare multiple capabilities using repeatable @Capability annotations (or the explicit @Capabilities container annotation).

**Multi-capability handlers:** When a handler declares multiple capabilities, a single handler instance serves all declared capabilities. The handler receives events with `Event.getTarget()` set to the invoked capability name, allowing it to distinguish which capability was called.

**Example (single capability):** A message-reading capability declared as `@Capability(value="get-message-content", version=1)` or simply `@Capability("get-message-content")` (version defaults to 1).

**Example (multi-capability handler):**
```java
@Capability("to-upper")
@Capability("to-lower")
public class CaseHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) {
        String input = (String) event.getPayload();
        return "to-upper".equals(event.getTarget())
            ? processUpper(input)
            : processLower(input);
    }
}
```

**Uniqueness constraint:** Each capability identifier must be unique within a microlith. Two different handlers cannot declare the same capability name. A single handler cannot declare the same capability twice.

**Note:** Future enhancements may add routing metadata (HTTP method, path, service grouping) to the annotation. Current implementation uses simple string identifiers.
### RemoteCapability
A mapping from capability name to remote endpoint URI, enabling inter-microlith communication. Represents a capability hosted in a different process or service.

**Structure:** `RemoteCapability(name: String, uri: URI)` where name is the capability identifier and uri is the remote endpoint.

**Purpose:** Enables EventBob to route events to handlers in other microliths. The transport mechanism (HTTP, gRPC) is determined by the URI scheme.

**Example:** `RemoteCapability("upper", URI.create("http://text-processing-service:8080"))` routes "upper" capability requests to a remote service.

### HandlerLoader
The mechanism for loading microservices from JARs or remote endpoints. Discovers EventHandler implementations and registers them with EventBob.

**Contract:** `Map<String, EventHandler> loadHandlers()` - parameterless method that uses constructor-injected configuration (JAR paths, remote endpoints) to discover and instantiate handlers.

**Implementations:**
- **JarHandlerLoader** - loads handlers from JAR files using isolated class loaders
- **RemoteHandlerLoader** - creates HTTP adapter wrappers for remote capability endpoints

### Event
A transport envelope for in-process communication between handlers within a microlith. **Not a domain event.** An Event contains routing information (source, target), parameters, metadata, and an optional payload. Events are immutable messages passed between EventHandler implementations via EventBob.

**Important distinction:** "Event" in EventBob refers to the message envelope, not to domain events in the Event Sourcing sense. Think of it as a request/response wrapper, not an occurrence that happened in the domain.

### EventDto
Data Transfer Object for Event serialization across HTTP boundaries. Translates between domain Event (core) and JSON representations (infrastructure).

**Purpose:** Keeps Jackson annotations and REST concerns out of the domain Event class. Acts as anti-corruption layer at HTTP boundary.

**Usage:** Infrastructure code converts EventDto ↔ Event at the system edge. Core domain never sees EventDto.

### Dispatcher
A facility provided to EventHandlers to send events to other handlers. The Dispatcher abstracts away the routing mechanism.

**Two send semantics:**
- **Async**: `CompletableFuture<Event> send(Event, BiFunction<Throwable, Event, Event>)` - returns immediately with a future, caller controls timeout and error handling
- **Sync**: `Event send(Event, BiFunction<Throwable, Event, Event>, long)` - blocks until response or timeout, convenience method for synchronous request-response patterns

**Timeout behavior:** The sync variant throws EventHandlingException on timeout, InterruptedException (with interrupt flag restored), or handler failure.

---

## Bounded Context

EventBob is **one bounded context** with a single ubiquitous language.

### Core Domain Model (io.eventbob.core)

The core defines domain concepts and port interfaces:

**Domain Concepts:**
- Microlith: Logical deployment unit bundling multiple capabilities
- Microservice: Code in a JAR that integrates with EventBob via EventHandler implementations
- EventHandler: Integration contract that microservices implement (single method: `handle(Event, Dispatcher)`)
- Capability: Identifier (string + version) declared via @Capability annotation
- HandlerLoader: Mechanism for loading microservices from various sources (JARs, remote endpoints)
- Event: Message envelope for in-process communication
- Dispatcher: Facility provided to EventHandlers for sending events to other handlers

**Core Components:**
- EventBob: Routes events to handlers
- HandlerLoader: Port interface for loading handlers from various sources

**Boundaries:**
- Core has NO framework dependencies (no Spring, no database libraries)
- Core does NOT know about HTTP, gRPC, PostgreSQL, or infrastructure details
- Core defines WHAT (domain model), not HOW (implementation)

### Infrastructure Implementations

**io.eventbob.spring** provides Spring Boot integration for EventBob server deployment. Responsible for:
- Loading microservices from JAR files at startup
- Discovering EventHandler implementations via @Capability annotations
- Registering handlers with EventBob router
- Providing HTTP adapters for remote capability invocation (RemoteHandlerLoader, HttpEventHandlerAdapter)

**Bridge Pattern:** Infrastructure depends on core, never reverse. Spring types stay in infrastructure layer, core types stay in core.

**Not a separate bounded context:** io.eventbob.spring uses the SAME ubiquitous language as core. It's an adapter, not a different semantic model.

### Inter-Microlith Communication

**Pattern:** Microliths communicate via HTTP by wrapping remote endpoints as EventHandler implementations.

**Anti-Corruption Layer:** HttpEventHandlerAdapter + EventDto translate between HTTP and domain Event at the boundary. Core routing logic remains unchanged.

**Location transparency:** Remote handlers are indistinguishable from local handlers at the EventBob routing level. Both implement EventHandler interface.

**Configuration:** RemoteCapability definitions declare which capabilities are hosted remotely. RemoteHandlerLoader instantiates HTTP adapters for those capabilities.

---

## Domain Invariants

1. **Capabilities are unique** — Each capability identifier (value + version) must be unique within a microlith
2. **Microservices must implement EventHandler** — Integration with EventBob requires one or more EventHandler implementations annotated with @Capability
3. **Location transparency preserved** — Remote handlers must implement the same EventHandler contract as local handlers. No special-case routing logic.

---

## What EventBob Is NOT

- **NOT bidirectional** — Not "deploy together OR separately"
- **NOT about deployment flexibility** — It's specifically for fixing over-distributed architectures
- **NOT a general-purpose tool** — It's a solution to a specific problem (too many microservices)
