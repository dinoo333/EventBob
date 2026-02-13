# EventBob Implementation Notes

## Code Quality Standards

### Design Principles Applied

1. **Boring is Better** - Constants over enums, flat structures over hierarchies, obvious over clever
2. **Immutability by Default** - Event is immutable with defensive copying
3. **Builder Pattern** - Event and routers use builders for construction
4. **Single Responsibility** - Each class has one reason to change
5. **Dependency Inversion** - Core defines interfaces, outer layers implement

### Testing Conventions

**Framework:** JUnit 5 + AssertJ

**Test Naming:** `@DisplayName("Given... when... then...")`
- Tests read as sentences describing behavior
- Method names use underscores: `standardMetadataKeysWork()`

**Test Structure:**
```java
@Test
@DisplayName("Given X, when Y, then Z")
void testMethodName() {
  // Arrange
  // Act
  // Assert
}
```

**Example:**
```java
@DisplayName("Given standard metadata keys, when building an event, then metadata is populated correctly")
void standardMetadataKeysWork() { ... }
```

**Test Patterns:**
- Use simple stubs over mocks (e.g., RecordingHandler)
- Tests are infrastructure-free (no web servers, no databases)
- Tests verify behavior, not implementation details

### Code Patterns

#### Immutable Value Objects

**Pattern:**
```java
public final class Event {
  private final String source;  // immutable fields
  private final String target;

  private Event(Builder builder) {  // private constructor
    this.source = builder.source;
    this.target = builder.target;
  }

  public Builder toBuilder() {  // copy-on-write
    return new Builder()
      .source(this.source)
      .target(this.target);
  }

  public static Builder builder() {  // static factory
    return new Builder();
  }

  public static final class Builder {
    // mutable fields + fluent setters
  }
}
```

**Applied in:** Event

#### Defensive Copying

**Pattern:**
```java
private static Map<String, Serializable> copy(Map<String, Serializable> in) {
  if (in == null || in.isEmpty()) return Collections.emptyMap();
  return Collections.unmodifiableMap(new LinkedHashMap<>(in));
}
```

**Rationale:** External callers cannot modify internal state after construction.

**Applied in:** Event (parameters, metadata maps)

#### Constants Class

**Pattern:**
```java
public final class MetadataKeys {
  public static final String CORRELATION_ID = "correlation-id";

  private MetadataKeys() {
    throw new AssertionError("No instances");
  }
}
```

**Rationale:**
- Prevents typos in string keys
- Documents metadata vocabulary
- IDE autocomplete for discoverability

**Applied in:** MetadataKeys

#### Composable Behavior via Interface

**Pattern:**
```java
public interface EventHandler {
  Event handle(Event event) throws EventHandlingException;
}

// Router IS-A handler, enabling nesting
public class EventHandlingRouter implements EventHandler { ... }

// Decorator wraps any handler
public class DecoratedEventHandler implements EventHandler { ... }
```

**Rationale:** Composition over inheritance. Handlers can be nested/wrapped without modifying code.

**Applied in:** EventHandler, EventHandlingRouter, DecoratedEventHandler

### Module Structure

**Current:**
```
io.eventbob.core/
  src/main/java/io/eventbob/core/
    eventrouting/                      # Event Routing subdomain
      Event.java
      EventHandler.java
      EventHandlingRouter.java
      DecoratedEventHandler.java
      EventHandlerCapability.java      # Annotation for registration
      MetadataKeys.java
      EventHandlingException.java
      HandlerNotFoundException.java
      UnexpectedEventHandlingException.java
    endpointresolution/                # Endpoint Resolution subdomain
      Capability.java                  # READ/WRITE/ADMIN enum
      CapabilityResolver.java          # Port/interface
      RoutingKey.java                  # Service operation identifier
      Endpoint.java                    # Physical endpoint address
      EndpointState.java               # GREEN/BLUE deployment state
  src/test/java/io/eventbob/core/
    EventTest.java
    EventHandlingRouterTest.java
    DecoratedEventHandlerTest.java
    MetadataKeysTest.java
    CoreArchitectureTest.java          # ArchUnit boundary enforcement
  docs/
    core_dependency_graph.md           # Generated Martin metrics
```

**Package Organization:**
- **Bounded context packages** — eventrouting/ and endpointresolution/ are subdomains within the Event Routing bounded context
- **Dependency direction** — eventrouting → endpointresolution (one-way, enforced by ArchUnit)
- **Exceptions flattened** — Exceptions live in eventrouting/ package directly (part of routing domain language)

**Convention:**
- Production code: `src/main/java/io/eventbob/core/{subdomain}/`
- Test code: `src/test/java/io/eventbob/core/`
- Test class name = `{ProductionClass}Test.java`

### Naming Conventions

**Classes:**
- PascalCase
- Nouns for data structures (Event, MetadataKeys)
- Nouns for behaviors when interface (EventHandler, not IEventHandler)
- Verbs for implementations when action-oriented (EventHandlingRouter)

**Methods:**
- camelCase
- Verbs for actions (handle, build, toBuilder)
- Getters without "get" when obvious (Event.source(), not Event.getSource()) - **Actually uses get***: Event has getSource(), getTarget(), etc.

**Constants:**
- UPPER_SNAKE_CASE
- Descriptive (CORRELATION_ID, not CORR_ID)

**Test Methods:**
- snake_case for readability
- Or camelCase with clear names (standardMetadataKeysWork)
- Always paired with @DisplayName for human-readable description

### Exception Handling

**Hierarchy:**
```
RuntimeException
  └─ io.eventbob.core.eventrouting.EventHandlingException
       ├─ io.eventbob.core.eventrouting.HandlerNotFoundException
       └─ io.eventbob.core.eventrouting.UnexpectedEventHandlingException
```

**Design Decisions:**
- RuntimeException base (unchecked) - callers decide whether to catch
- EventHandlingException carries optional Serializable payload for diagnostics
- Specific subclasses for specific failure modes
- **Exceptions are part of eventrouting subdomain** - they describe routing failures (handler not found, unexpected routing error), not general endpoint resolution failures

**Pattern:**
```java
public class EventHandlingException extends RuntimeException {
  private final Serializable payload;

  public EventHandlingException(String message, Serializable payload) {
    super(message);
    this.payload = payload;
  }

  public Serializable getPayload() { return payload; }
}
```

**Rationale for flattening:**
Previously exceptions were in a separate `exceptions/` subpackage. They were moved into `eventrouting/` directly because:
1. Exceptions are part of the routing domain language (not infrastructure)
2. Flattening eliminates cyclic dependency risk (eventrouting ↔ exceptions)
3. Simpler package structure with cohesive domain concepts

### JavaDoc Standards

**Required for:**
- All public classes
- All public methods
- All constants that require explanation

**Format:**
```java
/**
 * Single-line summary (period at end).
 *
 * <p>Extended description paragraph. Can use HTML tags.
 *
 * <p><b>Type:</b> String (typically UUID)
 * <br><b>Example:</b> {@code "550e8400-e29b-41d4-a716-446655440000"}
 * <br><b>Required:</b> For cross-macro calls
 */
```

**Applied in:** MetadataKeys (comprehensive JavaDoc with examples)

## Current Implementation Status

### Implemented
- ✅ Event immutable value object with builder
- ✅ EventHandler interface (synchronous request-response)
- ✅ EventHandlingRouter with exact string matching
- ✅ DecoratedEventHandler for cross-cutting concerns
- ✅ Exception hierarchy
- ✅ MetadataKeys vocabulary
- ✅ Test coverage for all components

### Planned (Not Yet Implemented)
- ⏳ Adapter modules (HTTP, gRPC, queue)
- ⏳ Route registry (local vs remote routing)
- ⏳ Classloader isolation for services
- ⏳ Macro-service configuration and bootstrap
- ⏳ Correlation tracking across macros
- ⏳ Async event patterns (fire-and-forget, queues)

## Known Technical Debt

**None currently.** The codebase is in early stage with clean foundations.

## Implementation Patterns to Establish (When Adapters Arrive)

### Pattern: Transport Metadata Namespacing

**When:** First adapter is implemented
**What:** Document the convention for transport-specific metadata keys
**Example:** HTTP adapter uses `http.status`, `http.content-type`

### Pattern: Request/Response Correlation

**When:** First remote adapter is implemented
**What:** Establish how correlation-id and reply-to flow through adapters
**Example:** Adapter tracks in-flight requests, matches responses by correlation-id

### Pattern: Error Translation

**When:** First adapter is implemented
**What:** How HTTP 404/500 map to EventHandlingException subtypes
**Example:** 404 → HandlerNotFoundException, 500 → UnexpectedEventHandlingException

### Pattern: Serialization Strategy

**When:** First adapter sends events over network
**What:** JSON? Protobuf? How is Event serialized/deserialized?
**Decision:** Likely JSON for universality, Protobuf for performance if needed

## Code Smells to Watch For

### As System Grows

**Speculative Generality:** Adding hooks "just in case" before they're needed. YAGNI.

**Primitive Obsession:** If metadata/parameters grow complex, consider value objects.

**Shotgun Surgery:** If one change requires editing many files, extract shared abstraction.

**Feature Envy:** If code in one package accesses data in another excessively, consider moving.

**Middle Man:** If classes just delegate without adding value, remove indirection.

### Current Status: No Smells Detected

The codebase is small, focused, and well-structured. All classes have clear responsibilities.

## Performance Considerations

**Current:** All operations are synchronous and in-memory. No performance concerns at this scale.

**Future Considerations:**
- **Router lookup:** Currently `Map.get(target)` is O(1). For complex routing (wildcards, regex), may need trie or pattern matcher.
- **Metadata copying:** Event uses defensive copying. For high-throughput, consider immutable collections library.
- **Classloader overhead:** Isolated classloaders add memory overhead. Measure before optimizing.
- **Serialization:** Network adapters will serialize Event. Benchmark JSON vs Protobuf vs MessagePack.

**Rule:** Measure before optimizing. No premature optimization.

## Migration Strategy (Breaking Changes)

**If Event structure changes:**
1. Add new field with default value
2. Deprecate old field
3. Support both for 1-2 versions
4. Remove old field in major version bump

**If EventHandler signature changes:**
1. Create new interface (EventHandler2)
2. Support both interfaces in router
3. Migrate implementations gradually
4. Remove old interface in major version bump

**Currently:** No migrations needed. Clean slate.

## Debugging Tips

**Enable detailed logging:**
```java
DecoratedEventHandler handler = DecoratedEventHandler.builder()
  .delegate(actualHandler)
  .before(event -> System.out.println("Handling: " + event))
  .afterSuccess((req, res) -> System.out.println("Success: " + res))
  .onError((req, err) -> System.err.println("Error: " + err))
  .build();
```

**Inspect metadata:**
```java
System.out.println("Event metadata:");
event.getMetadata().forEach((k, v) ->
  System.out.println("  " + k + " = " + v)
);
```

**Check test naming:**
If a test fails, the @DisplayName tells you exactly what scenario broke.

## Dependencies

**Core module:** ZERO runtime dependencies
- JUnit 5 (test only)
- AssertJ (test only)

**Future adapter modules will add:**
- HTTP client libraries
- gRPC libraries
- Queue client libraries

**Policy:** Keep core dependency-free. Adapters own their transport dependencies.

---

## Bridge Pattern Implementation Guidelines

### Port Design Standards (For Core Developers)

When adding new ports to `io.eventbob.core`:

**1. Interface-Only Contracts**
```java
// ✅ GOOD: Pure interface in core
public interface CapabilityResolver {
    List<Endpoint> resolveEndpoints(RoutingKey key);
}

// ❌ BAD: Abstract class with implementation
public abstract class BaseCapabilityResolver {
    protected final DataSource dataSource; // Leaks infrastructure!
}
```

**2. Framework-Agnostic Types**
```java
// ✅ GOOD: Core types only
public interface CapabilityResolver {
    List<Endpoint> resolveEndpoints(RoutingKey key);
}

// ❌ BAD: Framework-specific types
public interface CapabilityResolver {
    List<Endpoint> resolveEndpoints(RoutingKey key, JdbcTemplate jdbc); // Spring!
}
```

**3. Return Core Types, Not Primitives**
```java
// ✅ GOOD: Type-safe, self-documenting
public interface CapabilityResolver {
    List<Endpoint> resolveEndpoints(RoutingKey key);
}

// ❌ BAD: Primitives require documentation to understand
public interface CapabilityResolver {
    List<String> resolveEndpoints(String serviceName, String capability, String method, String path);
}
```

**4. Design for Multiple Implementations**
- Assume at least two implementations (Spring, Dropwizard) will exist
- Don't add Spring-specific assumptions to port contracts
- If a parameter is Spring-specific, it doesn't belong in the port

### Anti-Corruption Layer Patterns

When implementing core ports in framework-specific modules:

**Pattern 1: Type Translation**
```java
// In io.eventbob.spring (implementation)
@Service
public class SpringCapabilityResolver implements CapabilityResolver {

    @Override
    public List<Endpoint> resolveEndpoints(RoutingKey key) {
        // Query database using Spring JDBC
        List<EndpointRow> rows = jdbcTemplate.query(...); // Spring type

        // Translate to core types before returning
        return rows.stream()
            .map(this::toEndpoint) // ACL translation
            .collect(Collectors.toList());
    }

    private Endpoint toEndpoint(EndpointRow row) { // ACL
        return new Endpoint(
            row.getUri(),
            row.getDeploymentVersion(),
            translateState(row.getDeploymentState()) // State translation
        );
    }

    private EndpointState translateState(DeploymentState state) {
        // Internal states GRAY/RETIRED never cross to core
        return switch (state) {
            case BLUE -> EndpointState.BLUE;
            case GREEN -> EndpointState.GREEN;
            case GRAY, RETIRED -> null; // Filtered out before this
        };
    }
}
```

**Pattern 2: State Filtering**
```java
// ✅ GOOD: Filter implementation-specific states before ACL
List<EndpointRow> rows = jdbcTemplate.query(
    "SELECT * FROM endpoints WHERE state IN ('BLUE', 'GREEN')" // Filter at query
);

// ❌ BAD: Exposing internal states to core
List<EndpointRow> rows = jdbcTemplate.query(
    "SELECT * FROM endpoints" // Gets GRAY/RETIRED too
);
return rows.stream()
    .map(this::toEndpoint) // Now ACL must handle GRAY/RETIRED!
```

**Pattern 3: Exception Translation**
```java
@Override
public List<Endpoint> resolveEndpoints(RoutingKey key) {
    try {
        return jdbcTemplate.query(...);
    } catch (DataAccessException e) { // Spring exception
        // Translate to core exception type
        throw new EndpointResolutionException(
            "Failed to resolve endpoints for " + key,
            e // Include cause for debugging
        );
    }
}
```

### Implementation Quality Standards

**Checklist for New Implementation Modules:**

- [ ] **Zero leakage to core** - Run ArchUnit test to verify core doesn't import from this module
- [ ] **Implement all required ports** - Every port in core that needs this infra must have impl
- [ ] **Anti-Corruption Layer for all types** - Framework types translated at boundary
- [ ] **Tests don't require core changes** - Test with Testcontainers, not mocks in core
- [ ] **Documentation explains Bridge Pattern** - README clarifies this is an implementation, not the only way
- [ ] **Follows core naming conventions** - Use core's ubiquitous language in implementation
- [ ] **Dependency direction correct** - Implementation → Core, never reverse (verify with ArchUnit)

### Forbidden Patterns in Core

**❌ NEVER do these in `io.eventbob.core`:**

**1. Import Framework Types**
```java
// ❌ BAD: Framework type in core
import org.springframework.jdbc.core.JdbcTemplate;

public interface CapabilityResolver {
    List<Endpoint> resolve(JdbcTemplate jdbc); // NO!
}
```

**2. Reference Implementation Modules**
```java
// ❌ BAD: Core knows about implementations
import io.eventbob.spring.SpringCapabilityResolver;

public class SomeCoreConcept {
    private SpringCapabilityResolver resolver; // NO!
}
```

**3. Add Implementation-Specific Logic**
```java
// ❌ BAD: Core doing Spring-specific work
public class Event {
    @Autowired // Spring annotation in core!
    private SomeBean bean;
}
```

**4. Expose Internal Implementation States**
```java
// ❌ BAD: GRAY/RETIRED are Spring implementation details
public enum EndpointState {
    GREEN, BLUE, GRAY, RETIRED // Last two don't belong in core
}

// ✅ GOOD: Only states that routing needs to know about
public enum EndpointState {
    GREEN, BLUE // Core only sees production and canary
}
```

### Creating a New Implementation Module

**Step-by-step guide for adding io.eventbob.dropwizard:**

1. **Create module structure**
   ```
   io.eventbob.dropwizard/
   ├── pom.xml (depends on io.eventbob.core)
   ├── src/main/java/io/eventbob/dropwizard/
   │   ├── DropwizardCapabilityResolver.java
   │   ├── DropwizardCapabilityRegistrar.java
   │   ├── DropwizardBootstrap.java
   │   └── ...
   ├── docs/
   │   ├── architecture.md (explain this is Bridge impl)
   │   ├── domain_spec.md (clarify NOT a bounded context)
   │   └── implementation_notes.md
   └── README.md (title: "EventBob Dropwizard Implementation")
   ```

2. **Implement core ports**
   - `CapabilityResolver` using JDBI instead of Spring JDBC
   - Translate JDBI `ResultSet` → core `Endpoint` via ACL

3. **Add Anti-Corruption Layer**
   - Create `DropwizardTypes → CoreTypes` translation
   - Filter implementation-specific states before returning to core

4. **Write ArchUnit tests**
   - Verify `io.eventbob.dropwizard` → `io.eventbob.core` (allowed)
   - Verify `io.eventbob.core` → `io.eventbob.dropwizard` (forbidden)
   - Verify `io.eventbob.spring` ↔ `io.eventbob.dropwizard` (forbidden)

5. **Document Bridge Pattern**
   - README explains: "This is the Dropwizard implementation of EventBob"
   - Architecture.md explains dependency on core
   - Not "registry using Dropwizard" but "EventBob implemented using Dropwizard"

6. **Test with Testcontainers**
   - Don't add mocks to core for testing
   - Use real PostgreSQL via Testcontainers
   - Verify capability registration, conflict detection, endpoint resolution

7. **Update top-level docs**
   - Add Dropwizard to "Choosing an Implementation" section
   - Update diagrams showing both Spring and Dropwizard implementations

### When to Extract to Core vs Keep in Implementation

**Extract to core when:**
- The concept is framework-agnostic (Event, EventHandler, Capability)
- Multiple implementations will use it the same way (RoutingKey structure)
- It's part of the domain model, not infrastructure (EndpointState.BLUE/GREEN)

**Keep in implementation when:**
- The concept is framework-specific (JdbcTemplate, JDBI Handle)
- Different implementations will do it differently (JAR scanning, persistence strategy)
- It's an infrastructure detail (DeploymentState.GRAY/RETIRED, heartbeat intervals)

**Example:**
- `Capability.READ/WRITE/ADMIN` → Core (domain concept, all impls use it)
- `DeploymentState.GRAY/RETIRED` → Implementation (rollout detail, not in routing contract)

### Anti-Pattern Recognition

**Smell 1: "Just add this small Spring thing to core"**
- Diagnosis: Violates dependency direction
- Fix: Add to implementation, translate to core types via ACL

**Smell 2: "Core types don't have what I need"**
- Diagnosis: May indicate missing abstraction OR implementation-specific need
- Fix: If all implementations need it → add to core. If Spring-specific → keep in Spring.

**Smell 3: "This ACL translation is repetitive"**
- Diagnosis: Good! ACLs prevent implementation details from leaking
- Fix: Accept the repetition. It's cheaper than coupling.

**Smell 4: "Tests in implementation require mocking core"**
- Diagnosis: Testing the wrong boundary
- Fix: Test the implementation directly with real infrastructure (Testcontainers)
