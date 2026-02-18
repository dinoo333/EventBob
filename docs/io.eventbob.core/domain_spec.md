# io.eventbob.core Domain Specification

## Module Domain Scope

This module implements the **Event Routing** bounded context - the core abstractions for routing events between handlers and loading microservices from various sources.

## Domain Concepts (Implemented)

### Event
A routable message carrying source, target, routing metadata, parameters, and payload. Immutable value object.

**Invariants:**
- source and target must be non-blank strings
- metadata and parameters are unmodifiable after construction
- Events can be transformed via toBuilder() (copy-on-write)

### EventHandler
The integration contract that microservices must implement to integrate with EventBob. Single method: `Event handle(Event, Dispatcher)`.

**Contract:** Synchronous, throws EventHandlingException on failure, returns Event response. The Dispatcher is provided so handlers can send events to other micro-services in the same or other microliths.

**Discovery:** EventBob discovers EventHandler implementations via @Capability annotations declared on the implementation class.

**Location transparency:** EventHandler implementations can be local (loaded from JARs) or remote (HTTP adapters wrapping remote endpoints). EventBob routing logic treats both identically.

### Dispatcher
A facility provided to EventHandlers in order to send events to other micro-services in the same or other microliths. The Dispatcher abstracts away the underlying transport mechanism (HTTP, gRPC, message queues) and provides a simple API for outbound event emission.

**Contract:** `CompletableFuture<Event> send(Event, BiFunction<Throwable, Event, Event> onError)` -- asynchronous send with error handling callback.

**Distinction from Router:** The Router routes inbound events to the correct handler within EventBob. The Dispatcher sends outbound events from within a handler to other services (whether co-located in the same microlith or remote).

### Capability (Annotation)
Metadata annotation that microservices use to declare what they provide.

**Current structure:**
- value: String - the capability identifier (e.g., "get-message-content", "create-message")
- version: int - version of the capability contract (default: 1)

**Purpose:** EventBob scans for @Capability annotations to discover which EventHandler implementations exist in loaded microservices.

**Example:** `@Capability(value="get-message-content", version=1)` or simply `@Capability("get-message-content")`.

**Note:** Current implementation uses simple string identifiers. Future enhancements may add routing metadata (service, method, path) to the annotation structure.

### HandlerLoader (Interface)
The domain abstraction for loading microservices from various sources. Responsible for:
- Loading handlers from JARs, remote endpoints, or other sources
- Discovering EventHandler implementations annotated with @Capability
- Instantiating discovered handlers (or creating adapters for remote handlers)

**Contract:** `Map<String, EventHandler> loadHandlers()` - parameterless method that uses constructor-injected dependencies (JAR paths, remote endpoints, HTTP clients) to discover and instantiate handlers.

**Return value:** Map is keyed by capability name (from @Capability annotation value), not by handler class. This allows multiple handlers per source, each with a unique capability identifier.

**Implementations:**
- **JarHandlerLoader** - loads handlers from JAR files using isolated class loaders
- **RemoteHandlerLoader** (io.eventbob.spring) - creates HTTP adapter wrappers for remote capabilities

**Design rationale:** Parameterless `loadHandlers()` with constructor injection enables different loader implementations to accept different configuration types (Collection<Path> for JARs, List<RemoteCapability> for remote endpoints) without forcing a common parameter type.

### Microservice (Concept)
Code packaged in a JAR that integrates with EventBob by implementing EventHandler interface(s).

**Not a class:** "Microservice" is the conceptual term for what lives in the JAR. The JAR contains one or more EventHandler implementations.

**Integration requirement:** A JAR may contain multiple EventHandler implementations, each annotated with a unique @Capability value. At least one handler is required for the JAR to be valid.

### Routing Semantics (MetadataKeys)
- **correlation-id** - UUID tracking request/response across boundaries
- **reply-to** - Address for response routing
- **method** - Operation type (POST, GET, UpdateItem, etc.)
- **path** - Resource path template

### Trace Context (MetadataKeys)
- **trace-id** - Distributed trace identifier
- **span-id** - Span identifier within trace

## Domain Principles

### Location Transparency

Handlers can be local (loaded from JARs in-process) or remote (accessed via HTTP/gRPC). EventBob's routing layer treats both identically—the location is an implementation detail hidden behind the EventHandler interface.

**Enforcement:** All handlers must implement the same EventHandler contract. Remote handlers use the Adapter pattern (HttpEventHandlerAdapter wraps remote calls and implements EventHandler).

**Consequence:** Capabilities can be relocated between microliths without changing EventBob routing logic. Only configuration changes (which HandlerLoader to use).

**Vocabulary:** Core domain says "handler" and "capability," not "local handler" or "remote capability." Location is infrastructure concern.

## Domain Concepts (Future)

### Microlith-Service
**Status:** Not yet modeled in code. Deployment concept only.

A process containing multiple microservices communicating via EventBob.

### Adapter
**Status:** Partially implemented in io.eventbob.spring.

Translation layer between EventBob and external transports (HTTP, gRPC, queues). HttpEventHandlerAdapter is the first adapter implementation.

## Ubiquitous Language (This Module)

- **Microservice** - code in a JAR that integrates via EventHandler implementations (not just "service")
- **EventHandler** - the integration contract microservices implement (not "processor", not "service")
- **Capability** - what a handler provides, declared via @Capability annotation (not "operation", not "endpoint")
- **HandlerLoader** - loads handlers from various sources (not "ServiceLoader", not "JarLoader")
- **Event** - not "message", not "request" (the vocabulary is intentionally event-based)
- **Router** - routes inbound events to the correct handler within EventBob; not "gateway"
- **Dispatcher** - provided to EventHandlers for sending events to other micro-services in the same or other microliths; not "emitter", not "sender"
- **Metadata** - infrastructure concerns (routing, observability)
- **Parameters** - business data
- **Location transparency** - remote and local handlers are indistinguishable at the routing level

## Domain Invariants

1. **EventHandler contract is universal** - All handlers (local or remote) must implement `Event handle(Event, Dispatcher)`. No exceptions.
2. **Capability names are unique within a microlith** - Two handlers cannot declare the same capability identifier (value + version).
3. **HandlerLoader uses constructor injection** - All configuration (JAR paths, remote endpoints) provided via constructor. `loadHandlers()` is parameterless.
4. **Remote handlers use Adapter pattern** - Remote capabilities are wrapped in EventHandler implementations (e.g., HttpEventHandlerAdapter). Core routing logic never knows about HTTP.

## Anti-Corruption Layers

**Remote handler communication:** HttpEventHandlerAdapter + EventDto (in io.eventbob.spring) translate between HTTP and domain Event at the boundary. Core Event routing logic remains unchanged.

**Not yet applicable for other transports:** When gRPC, JMS, or Kafka adapters are added, they will follow the same pattern: implement EventHandler, translate at boundary, keep transport details out of core.
