# io.eventbob.core Domain Specification

## Module Domain Scope

This module implements the **Event Routing** bounded context - the core abstractions for routing events between handlers and loading microservices from JARs.

## Domain Concepts (Implemented)

### Event
A routable message carrying source, target, routing metadata, parameters, and payload. Immutable value object.

**Invariants:**
- source and target must be non-blank strings
- metadata and parameters are unmodifiable after construction
- Events can be transformed via toBuilder() (copy-on-write)

### EventHandler
The integration contract that microservices must implement to integrate with EventBob. Single method: `Event handle(Event, Dispatcher)`.

**Contract:** Synchronous, throws EventHandlingException on failure, returns Event response. The Dispatcher is provided so handlers can send events to other micro-services in the same or other macroliths.

**Discovery:** EventBob discovers EventHandler implementations via @Capability annotations declared on the implementation class.

### Dispatcher
A facility provided to EventHandlers in order to send events to other micro-services in the same or other macroliths. The Dispatcher abstracts away the underlying transport mechanism (HTTP, gRPC, message queues) and provides a simple API for outbound event emission.

**Contract:** `CompletableFuture<Event> send(Event, BiFunction<Throwable, Event, Event> onError)` -- asynchronous send with error handling callback.

**Distinction from Router:** The Router routes inbound events to the correct handler within EventBob. The Dispatcher sends outbound events from within a handler to other services (whether co-located in the same macrolith or remote).

### Capability (Annotation)
Metadata annotation that microservices use to declare what they provide.

**Current structure:**
- value: String - the capability identifier (e.g., "get-message-content", "create-message")
- version: int - version of the capability contract (default: 1)

**Purpose:** EventBob scans for @Capability annotations to discover which EventHandler implementations exist in loaded microservices.

**Example:** `@Capability(value="get-message-content", version=1)` or simply `@Capability("get-message-content")`.

**Note:** Current implementation uses simple string identifiers. Future enhancements may add routing metadata (service, method, path) to the annotation structure.

### HandlerLoader (Interface)
The domain abstraction for loading microservices from JARs. Responsible for:
- Loading JAR files from filesystem
- Creating isolated class loaders for each JAR
- Discovering EventHandler implementations annotated with @Capability
- Instantiating discovered handlers

**Contract:** `Map<String, EventHandler> loadHandlers(Collection<Path> jarPaths)` - accepts multiple JAR paths, returns map of capability names to instantiated handlers.

**Return value:** Map is keyed by capability name (from @Capability annotation value), not by handler class. This allows multiple handlers per JAR, each with a unique capability identifier.

**Implementation:** Package-private `JarHandlerLoader` provides the concrete implementation. Details are hidden from other modules.

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

## Domain Concepts (Future)

### Macrolith-Service
**Status:** Not yet modeled in code. Deployment concept only.

A process containing multiple microservices communicating via EventBob.

### Adapter
**Status:** Not yet modeled in code. Future bounded context.

Translation layer between EventBob and external transports (HTTP, gRPC, queues).

## Ubiquitous Language (This Module)

- **Microservice** - code in a JAR that integrates via EventHandler implementations (not just "service")
- **EventHandler** - the integration contract microservices implement (not "processor", not "service")
- **Capability** - what a handler provides, declared via @Capability annotation (not "operation", not "endpoint")
- **HandlerLoader** - loads microservices from JARs (not "ServiceLoader", not "JarLoader")
- **Event** - not "message", not "request" (the vocabulary is intentionally event-based)
- **Router** - routes inbound events to the correct handler within EventBob; not "gateway"
- **Dispatcher** - provided to EventHandlers for sending events to other micro-services in the same or other macroliths; not "emitter", not "sender"
- **Metadata** - infrastructure concerns (routing, observability)
- **Parameters** - business data

## Anti-Corruption Layers

**Not yet applicable** - single module with no external dependencies. When adapters are added, they will translate between transport protocols and Event domain model.
