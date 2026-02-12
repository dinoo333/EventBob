# EventBob Domain Specification

## Core Domain: Event Routing

EventBob is a **distributed event routing fabric** that enables microservices to be deployed together (in-process routing) or separately (network routing via adapters), with deployment topology determined by configuration rather than code changes.

### Ubiquitous Language

#### Event
A routable message containing source, target, routing semantics, payload, and trace context. Events flow between services through the EventBob routing layer.

#### Routing Semantics
The information required to route an event to its destination:
- **target** - Service identifier (e.g., "user-service", "inventory-service")
- **method** - Operation type (POST, GET, UpdateInventory, OrderCreated)
- **path** - Resource path template (e.g., "users/{id}", "orders/{orderId}/items/{itemId}")
- **correlation-id** - UUID for tracking request/response pairs across macro boundaries
- **reply-to** - Address where response should be sent

#### Trace Context
Observability identifiers propagated through the event chain:
- **trace-id** - Distributed trace identifier (OpenTelemetry compatible)
- **span-id** - Span identifier for this event within the trace

#### Macro-Service
A deployment unit containing multiple microservices that communicate in-process via EventBob routing. The same microservice can participate in multiple macro-services.

#### Adapter
A translation layer between the EventBob routing fabric and external transports (HTTP, gRPC, message queues). Adapters implement EventHandler to participate in routing.

#### Service
An independently versioned component loaded via isolated classloader that implements EventHandler to process events.

### Bounded Contexts

#### Event Routing Context (Core)
**Responsibility:** Route events between services based on target, method, and path. Resolve service capabilities to physical endpoints for progressive deployment. Manage request/response correlation. Propagate trace context.

**Subdomains:**
- **Event Routing** (`eventrouting` package) — Route events by target to handlers. Handle event lifecycle, decoration, and failure modes.
- **Endpoint Resolution** (`endpointresolution` package) — Resolve service capabilities (READ/WRITE/ADMIN operations) to physical endpoint addresses for routing decisions. Support progressive deployment (GREEN/BLUE states).

**Vocabulary:**
- Event Routing: Event, EventHandler, EventHandlingRouter, DecoratedEventHandler, routing semantics (target, method, path, correlation-id, reply-to), trace context (trace-id, span-id), exceptions (EventHandlingException, HandlerNotFoundException)
- Endpoint Resolution: Capability (READ/WRITE/ADMIN), CapabilityResolver (port/interface), RoutingKey (service + capability + method + path), Endpoint (physical URL), EndpointState (GREEN/BLUE)

**Boundaries:** Does NOT know about specific transports (HTTP, gRPC, queues). Does NOT know about persistence (registry database). Does NOT know about business domains (users, orders, inventory).

#### Transport Adapter Context
**Responsibility:** Translate between transport-specific protocols (HTTP requests, gRPC calls, queue messages) and EventBob events.

**Vocabulary:** Transport-specific (http.status, http.content-type, grpc.service, queue.topic). Each adapter defines its own namespace.

**Boundaries:** Depends on Event Routing Context. Implements EventHandler. Does NOT leak transport concepts into core.

#### Service Registry Context
**Responsibility:** Discover, register, and track service capabilities in macro-services. Maintain a live registry of which physical instances provide which operations. Enable capability-based endpoint resolution for progressive deployments.

**Type:** Supporting Subdomain (serves Event Routing Core Domain)

**Vocabulary:**
- Macro-Service - logical deployment unit bundling multiple service JARs
- Instance - single running process of a macro-service (macroName + instanceId)
- Capability Registration - discovery via JAR scanning (`@EventHandlerCapability` annotations)
- Deployment State - rollout lifecycle (BLUE, GREEN, GRAY, RETIRED)
- Instance Status - health state (HEALTHY, UNHEALTHY, DRAINING, TERMINATED)
- Routing Key - composite identifier (serviceName:capability:method:pathPattern)
- Conflict Detection - version mismatch between declared and registered capabilities

**Implements (future):** `CapabilityResolver` port (defined in Endpoint Resolution subdomain)

**Anti-Corruption Layer:** Translates internal `DeploymentState` (BLUE/GREEN/GRAY/RETIRED) to external `EndpointState` (BLUE/GREEN) when implementing CapabilityResolver. GRAY and RETIRED states never cross to core.

**Boundaries:** Depends on Event Routing Context (imports Capability enum, EventHandler interface, EventHandlerCapability annotation). Does NOT leak infrastructure concerns (PostgreSQL, Spring Boot, ClassGraph) into core.

#### Service Domain Context
**Responsibility:** Business logic processing. Interprets events using routing semantics (method, path) and parameters to perform domain operations.

**Vocabulary:** Domain-specific (User, Order, Inventory, etc.). Uses Event as the communication envelope.

**Boundaries:** Depends on Event Routing Context. Implements EventHandler. Does NOT know about other services' internal domain models.

### Anti-Corruption Layers

**Transport → Event Routing:** Adapters translate transport requests into Events, ensuring transport-specific concerns (HTTP status codes, gRPC metadata) remain in adapter metadata namespace, not core routing metadata.

**Service Registry → Event Routing:** Registry translates its internal deployment model (DeploymentState: BLUE/GREEN/GRAY/RETIRED) to core's routing model (EndpointState: BLUE/GREEN). The registry's infrastructure model (PostgreSQL schema, Spring Boot, ClassGraph) never crosses to core. Core sees only Endpoint + EndpointState via CapabilityResolver port.

**Event Routing → Service Domain:** Services interpret routing semantics (method, path) in domain-specific ways. A POST to "orders" might mean CreateOrder in one service, different operation in another.

### Domain Invariants

1. **Events are immutable** - Once created, an event's content cannot change. Transformations create new events.

2. **Target identifies service, not operation** - The target field specifies which service handles the event. The service interprets method/path to determine the specific operation.

3. **Correlation flows forward** - correlation-id is generated by the initiating adapter and propagated through all subsequent events in the chain.

4. **Trace context is passive** - trace-id and span-id are annotations for observability. They do NOT affect routing decisions.

5. **Metadata vs Parameters boundary** - Metadata carries routing and infrastructure concerns. Parameters carry business data and path parameter values.

### Current Architecture Decisions

**AD-001: Synchronous EventHandler Interface**
- Decision: EventHandler.handle(Event) returns Event synchronously
- Rationale: Start simple. Async patterns (queues, fire-and-forget) can be layered on top via adapters
- Status: Active

**AD-002: String-Based Metadata Keys**
- Decision: Metadata is Map<String, Serializable> with string keys
- Rationale: Flexible, transport-agnostic, allows adapters to extend without core changes
- Status: Active

**AD-003: Core Does Not Define Transport Namespaces**
- Decision: Removed HTTP_PREFIX, GRPC_PREFIX, QUEUE_PREFIX from core
- Rationale: Transport concerns belong in adapter layer. Core defines universal routing vocabulary only.
- Status: Active (resolved in MetadataKeys refactoring)

**AD-004: Target is Service-Level, Not Operation-Level**
- Decision: Target identifies a service. Method/path identify the operation within the service.
- Rationale: Enables hierarchical routing. Services control their own internal dispatch logic.
- Status: Planned (not yet implemented)

### Evolution & Future Directions

**Implemented:** Service Registry Context (2026-02-12) - JAR scanning, capability registration, instance tracking, deployment state management. Future: implement CapabilityResolver port for endpoint resolution.

**Next Bounded Context:** HTTP Adapter Context - when first HTTP adapter is built, will establish patterns for transport translation and metadata namespacing.

**Deferred Decision:** Whether METHOD and PATH are abstract routing concepts or HTTP-specific concepts that other transports translate. Will be clarified when gRPC adapter is built.

**Open Question:** Should routing metadata and observability metadata be separate enums/types, or remain unified under MetadataKeys? Currently unified; may split if pain is felt.
