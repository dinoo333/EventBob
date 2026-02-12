# io.eventbob.core Domain Specification

## Module Domain Scope

This module implements the **Event Routing** bounded context - the core abstractions for routing events between handlers.

## Domain Concepts (Implemented)

### Event
A routable message carrying source, target, routing metadata, parameters, and payload. Immutable value object.

**Invariants:**
- source and target must be non-blank strings
- metadata and parameters are unmodifiable after construction
- Events can be transformed via toBuilder() (copy-on-write)

### EventHandler
The universal abstraction for processing events. Single method: `Event handle(Event)`.

**Contract:** Synchronous, throws EventHandlingException on failure, returns Event response.

### Routing Semantics (MetadataKeys)
- **correlation-id** - UUID tracking request/response across boundaries
- **reply-to** - Address for response routing
- **method** - Operation type (POST, GET, UpdateItem, etc.)
- **path** - Resource path template

### Trace Context (MetadataKeys)
- **trace-id** - Distributed trace identifier
- **span-id** - Span identifier within trace

## Domain Concepts (Future)

### Macro-Service
**Status:** Not yet modeled in code. Deployment concept only.

A process containing multiple services communicating via EventBob.

### Service
**Status:** Not yet modeled in code. Will implement EventHandler.

An independently versioned component that processes events.

### Adapter
**Status:** Not yet modeled in code. Future bounded context.

Translation layer between EventBob and external transports (HTTP, gRPC, queues).

## Ubiquitous Language (This Module)

- **Event** - not "message", not "request" (the vocabulary is intentionally event-based)
- **Handler** - not "processor", not "service" (the pattern name)
- **Router** - not "dispatcher", not "gateway"
- **Metadata** - infrastructure concerns (routing, observability)
- **Parameters** - business data

## Anti-Corruption Layers

**Not yet applicable** - single module with no external dependencies. When adapters are added, they will translate between transport protocols and Event domain model.
