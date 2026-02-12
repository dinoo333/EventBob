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

### Constants Class

**Applied in:** MetadataKeys

**Pattern:**
- `public static final String` constants
- `private` constructor throwing `AssertionError`
- Grouped by concern (routing, observability)
- Comprehensive JavaDoc with examples

### Composable Handlers

**Applied in:** EventHandler, EventHandlingRouter, DecoratedEventHandler

**Pattern:**
- Single interface: `Event handle(Event)`
- Router implements EventHandler (enables nesting)
- Decorator implements EventHandler (enables wrapping)
- No handler subclasses - pure composition

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
- EventHandlingRouterTest - tests routing by target, error cases
- DecoratedEventHandlerTest - tests before/after/error hooks
- MetadataKeysTest - tests constant usage with Event

**Stub pattern:**
```java
class RecordingHandler implements EventHandler {
  Event received;
  Event toReturn;

  @Override
  public Event handle(Event event) {
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
