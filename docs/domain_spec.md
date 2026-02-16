# EventBob Domain Specification

## What EventBob Is

EventBob is a **framework for bundling multiple microservices into a macrolith (macro-service)** to solve the chatty-application anti-pattern.

### The Problem It Solves

When too many microservices make excessive network calls to each other:
- Network becomes saturated
- Performance is dismal
- Engineers "did a shit job at architecting a system"

**First rule of distributed systems:** Don't distribute if you don't have to. This rule gets broken often. EventBob fixes the mess.

### How It Works

1. EventBob server loads multiple JARs containing microservice code
2. Exposes them as a single monolithic service (the macrolith)
3. Inside the macrolith: services communicate in-process (fast, no network overhead)
4. Outside: the macrolith acts like a regular microservice (discovered via service registry)

**Key insight:** EventBob is still a server acting as a microservice. Just bigger and less chatty.

### Architecture Compliance

EventBob remains **compliant with microservice architecture**. It just allows engineers to bundle things together in a more performant way—fewer network calls, faster execution.

---

## Ubiquitous Language

### Macrolith
A deployment unit containing multiple microservice capabilities bundled together. A macrolith is a single server process that loads multiple JARs and exposes their combined functionality.

**Informal terms:** Sometimes called "macro" or "macro-service" in casual conversation, but "macrolith" is the formal ubiquitous language term.

**Example:** The "messages-service" macrolith bundles message-reading, message-writing, and message-deletion capabilities into one process.

### Capability
A routable operation that a service provides. Each capability is identified by:
- Service name (e.g., "messages")
- Capability type (READ, WRITE, ADMIN)
- Version (e.g., 1, 2)
- HTTP method (GET, POST, DELETE)
- Path pattern (e.g., "/content", "/content/{id}")

**Example:** The READ capability for messages might be `GET /content` handled by `MessagesReadHandler.class`.

### Service
An independently versioned component loaded via JAR that provides one or more capabilities.

### Event
A transport envelope for in-process communication between handlers within a macrolith. **Not a domain event.** An Event contains routing information (source, target), parameters, metadata, and an optional payload. Events are immutable messages passed between EventHandler implementations via the EventBob.

**Important distinction:** "Event" in EventBob refers to the message envelope, not to domain events in the Event Sourcing sense. Think of it as a request/response wrapper, not an occurrence that happened in the domain.

### Endpoint
A logical service URL where a macrolith can be reached. Not an IP address—a logical URL like "http://messages-service" that infrastructure resolves to physical addresses.

**Infrastructure handles resolution:** DNS, service mesh, or load balancers resolve "http://messages-service" → actual IPs (10.0.1.5:8080, 10.0.1.6:8080, etc.)

**Comparison:** Same pattern as Docker Swarm—services are resolved by name, not IP.

### Registry
Tracks which macroliths exist and what capabilities they provide. Does NOT track physical instances—that's infrastructure's job.

**What registry tracks:**
- Macrolith: "messages-service" → Endpoint: "messages-service"
  - Capability: READ /content
  - Capability: WRITE /content
  - Capability: DELETE /content/{id}

**What infrastructure tracks:**
- "messages-service" → [10.0.1.5:8080, 10.0.1.6:8080, 10.0.1.7:8080]

---

## Open Questions

1. **Transport abstraction:** How do external clients invoke capabilities? HTTP? gRPC? Message queues? The core is transport-agnostic, but no transport layer is implemented yet.
2. **Deployment model:** When should engineers bundle services into a macrolith vs keeping them separate? What are the decision criteria?

---

## Bounded Context

EventBob is **one bounded context** with a single ubiquitous language.

### Core Domain Model (io.eventbob.core)

The core defines domain concepts and port interfaces:

**Domain Concepts:**
- Macrolith: Logical deployment unit bundling multiple capabilities
- Capability: Routable operation identified by (service, capability, version, method, path)
- Endpoint: Logical service URL where a macrolith can be reached
- Event: Message envelope for in-process communication (source, target, parameters, metadata, payload)
- EventHandler: Handler interface with single `handle(Event)` method

**Ports (interfaces for infrastructure to implement):**
- CapabilityResolver: Resolves routing keys to endpoints
- EventBob: Routes events to handlers based on target string

**Boundaries:**
- Core has NO framework dependencies (no Spring, no database libraries)
- Core does NOT know about HTTP, gRPC, PostgreSQL, or infrastructure details
- Core defines WHAT (domain model), not HOW (implementation)

### Infrastructure Implementations

**io.eventbob.spring** implements core ports using Spring Boot:
- ServiceRegistryRepository: Capability persistence (PostgreSQL via Spring JDBC)
- MacroServiceBootstrap: JAR scanning and registration orchestration
- CapabilityScanner: Annotation-based capability discovery (ClassGraph library)

**Bridge Pattern:** Infrastructure depends on core, never reverse. Spring types stay in infrastructure layer, core types stay in core.

**Not a separate bounded context:** io.eventbob.spring uses the SAME ubiquitous language as core. It's an adapter, not a different semantic model.

---

## Domain Invariants

1. **Macroliths are logical, not physical** — Registry tracks macrolith names, not instance IPs
2. **Infrastructure resolves endpoints** — Endpoint is a logical URL; infrastructure translates to physical addresses
3. **Capabilities are unique** — Each routing key (service + capability + version + method + path) must be unique
4. **Idempotent registration** — Macroliths can re-register safely (e.g., pod restarts, rolling updates across instances)

---

## What EventBob Is NOT

- **NOT bidirectional** — Not "deploy together OR separately"
- **NOT about deployment flexibility** — It's specifically for fixing over-distributed architectures
- **NOT a general-purpose tool** — It's a solution to a specific problem (too many microservices)
