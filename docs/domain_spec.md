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

**Declaration:** Microservices declare capabilities via @Capability annotation on EventHandler implementations.

**Example:** A message-reading capability might be declared as `@Capability(value="get-message-content", version=1)` or simply `@Capability("get-message-content")` (version defaults to 1).

**Note:** Future enhancements may add routing metadata (HTTP method, path, service grouping) to the annotation. Current implementation uses simple string identifiers.

### HandlerLoader
The mechanism for loading microservices from JARs. Discovers EventHandler implementations and registers them with EventBob.

### Event
A transport envelope for in-process communication between handlers within a microlith. **Not a domain event.** An Event contains routing information (source, target), parameters, metadata, and an optional payload. Events are immutable messages passed between EventHandler implementations via EventBob.

**Important distinction:** "Event" in EventBob refers to the message envelope, not to domain events in the Event Sourcing sense. Think of it as a request/response wrapper, not an occurrence that happened in the domain.

### Dispatcher
A facility provided to EventHandlers to send events to other handlers. The Dispatcher abstracts away the routing mechanism.

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
- HandlerLoader: Mechanism for loading microservices from JARs
- Event: Message envelope for in-process communication
- Dispatcher: Facility provided to EventHandlers for sending events to other handlers

**Core Components:**
- EventBob: Routes events to handlers
- HandlerLoader: Loads microservices from JAR files

**Boundaries:**
- Core has NO framework dependencies (no Spring, no database libraries)
- Core does NOT know about HTTP, gRPC, PostgreSQL, or infrastructure details
- Core defines WHAT (domain model), not HOW (implementation)

### Infrastructure Implementations

**io.eventbob.spring** provides Spring Boot integration for EventBob server deployment. Responsible for:
- Loading microservices from JAR files at startup
- Discovering EventHandler implementations via @Capability annotations
- Registering handlers with EventBob router

**Bridge Pattern:** Infrastructure depends on core, never reverse. Spring types stay in infrastructure layer, core types stay in core.

**Not a separate bounded context:** io.eventbob.spring uses the SAME ubiquitous language as core. It's an adapter, not a different semantic model.

---

## Domain Invariants

1. **Capabilities are unique** — Each capability identifier (value + version) must be unique within a microlith
2. **Microservices must implement EventHandler** — Integration with EventBob requires one or more EventHandler implementations annotated with @Capability

---

## What EventBob Is NOT

- **NOT bidirectional** — Not "deploy together OR separately"
- **NOT about deployment flexibility** — It's specifically for fixing over-distributed architectures
- **NOT a general-purpose tool** — It's a solution to a specific problem (too many microservices)
