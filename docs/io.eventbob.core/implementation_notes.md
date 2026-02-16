# io.eventbob.core Implementation Notes

## Module-Specific Patterns

### Immutable Value Objects

**Applied in:** Event

**Pattern:**
- Private constructor taking Builder
- All fields `private final`
- Defensive copying for collections
- `toBuilder()` for copy-on-write transformations
- Static factory `builder()` for construction

**Type flexibility:**
- Event uses `Object` types for parameters, metadata, and payload fields
- Reflects JSON-compatible values without artificial `Serializable` constraint
- Honesty over false type safety: any Java object accepted, serialization concerns handled at boundaries

### Composable Handlers

**Applied in:** EventHandler, EventBob

**Pattern:**
- Single interface: `Event handle(Event event, Dispatcher dispatcher)`
- EventBob routes events to target-specific handlers
- Handlers registered via `EventBob.Builder.handler(String target, EventHandler handler)`
- No handler subclasses - pure composition

### Handler Registration Pattern

**Applied in:** EventBob.Builder

**Pattern:**
```java
EventBob router = EventBob.builder()
    .handler("echo", new EchoHandler())
    .handler("lowercase", new LowercaseHandler())
    .build();
```

- Target string identifies the handler (stored in Event.target field)
- Handlers are immutably registered at build time
- Map is defensively copied and made unmodifiable
- HandlerNotFoundException thrown if target not found

### Async Execution Model

**Applied in:** EventBob, Dispatcher

**Pattern:**
- All event processing returns `CompletableFuture<Event>`
- EventBob uses virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
- Handler invocation happens asynchronously on virtual thread pool
- Error handling via `exceptionally()` with BiFunction error callback
- Default error event created if callback returns null

**Key characteristics:**
- Non-blocking: Callers receive CompletableFuture immediately
- Virtual threads: High concurrency without thread pool tuning
- Error recovery: Errors converted to error events, not propagated as exceptions

## Testing Conventions (This Module)

**Test structure:**
```java
@Test
@DisplayName("Given X, when Y, then Z")
void testMethodName() {
  // Arrange, Act, Assert
}
```

**Examples:**
- EventTest - tests Event immutability, builder, defensive copying
- EventBobTest - tests routing by target, error cases
- HandlerNotFoundExceptionTest - tests exception when handler not found
- CapabilityTest - tests capability annotations
- DefaultErrorEventTest - tests error event creation

**Stub pattern:**
```java
class RecordingHandler implements EventHandler {
  Event received;
  Event toReturn;

  @Override
  public Event handle(Event event, Dispatcher dispatcher) {
    this.received = event;
    return toReturn != null ? toReturn : event;
  }
}
```

Simple stubs over mocks - verify behavior via recorded state.

## Code Quality

**Current status:** Clean baseline
- Zero external dependencies
- All classes have single responsibility
- Immutability enforced
- Defensive copying prevents external mutation
- Exception hierarchy is specific (HandlerNotFoundException vs UnexpectedEventHandlingException)

**No technical debt in this module.**

## Performance Characteristics

**Router:** O(1) map lookup by target string
**Event construction:** Defensive copy of metadata/parameters maps
**Memory:** Each Event allocates immutable copies of collections

**Not optimized for high throughput yet.** Baseline implementation prioritizes correctness and clarity.
