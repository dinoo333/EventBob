# io.eventbob.core Architecture

## Module Purpose

The core domain module defining event routing abstractions and primitives. This is the innermost layer - all other modules depend on core, core depends on nothing (except JDK).

## Current Implementation Status

**Implemented:**
- Event data structure (immutable value object)
- EventHandler interface (universal contract)
- EventBob (string-based routing)
- DecoratedEventHandler (cross-cutting concerns via decoration)
- Exception hierarchy
- MetadataKeys vocabulary

**Not Yet Implemented:**
- Route registry
- Service loading
- Classloader isolation
- Adapters (separate modules)

## Internal Structure

```
io.eventbob.core
├── Event                      (domain model)
├── EventHandler               (core abstraction)
├── EventBob        (routing implementation)
├── DecoratedEventHandler      (decorator pattern)
├── MetadataKeys              (vocabulary)
└── exceptions/
    ├── EventHandlingException
    ├── HandlerNotFoundException
    └── UnexpectedEventHandlingException
```

## Key Abstractions

### EventHandler Interface

The universal contract for event processing:

```java
public interface EventHandler {
  Event handle(Event event) throws EventHandlingException;
}
```

**Design:** Synchronous request-response. Single method. Composable (routers and decorators implement this).

### Event Structure

Immutable value object with builder:
- `source` - Producer identifier
- `target` - Consumer identifier
- `metadata` - Routing and observability metadata
- `parameters` - Business data
- `payload` - Request body

**Key invariant:** Metadata carries infrastructure concerns (correlation-id, method, path). Parameters carry business data.

### EventBob

Routes events by exact target match to registered handlers.

**Current limitation:** No hierarchical routing, no wildcards. Flat string lookup.

**Extension point:** Implements EventHandler, enabling router nesting.

### DecoratedEventHandler

Wraps any EventHandler with optional hooks:
- `before` - Executed before delegation
- `afterSuccess` - Executed after successful handling
- `onError` - Error recovery/transformation

**Use cases:** Logging, metrics, retry logic, auth checks.

### MetadataKeys

Constants defining standard routing and observability vocabulary:
- Routing: CORRELATION_ID, REPLY_TO, METHOD, PATH
- Observability: TRACE_ID, SPAN_ID

**Design decision:** Core defines only universal keys. Transport-specific keys (http.*, grpc.*) belong in adapter modules.

## Dependencies

**External:** ZERO runtime dependencies

**Internal:** Self-contained. No inter-package dependencies within module.

**Dependents:** Future adapter modules will depend on this module.

## Testing Strategy

- Unit tests for each component
- Behavioral tests using @DisplayName("Given... when... then...")
- Simple stubs over mocks (e.g., RecordingHandler)
- No infrastructure dependencies in tests

## Future Evolution

**When adapters are added:**
- Adapters will implement EventHandler
- Adapters will use MetadataKeys vocabulary
- Core module remains unchanged

**When routing grows complex:**
- May extract Router interface
- May add pattern-based routing (wildcards, regex)
- May add route registry abstraction

**When async is needed:**
- May add AsyncEventHandler interface
- May add EventPublisher for fire-and-forget
- Current EventHandler remains for sync cases
