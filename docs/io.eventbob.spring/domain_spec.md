# io.eventbob.spring Domain Specification

## Module Scope

This module provides Spring Boot infrastructure integration for EventBob server deployment.

## Ubiquitous Language

This module uses the **same ubiquitous language as core** (see top-level `docs/domain_spec.md`). No new domain concepts are introduced. This is an infrastructure adapter layer.

Key terms from core that apply here:
- **Event** - Message envelope for communication
- **EventHandler** - Handler interface for capability implementations
- **Capability** - Routable operation (declared via `@Capability` annotation)
- **EventBob** - Event router
- **Dispatcher** - Facility for sending events to other handlers

## Bounded Context

io.eventbob.spring is **not a separate bounded context**. It is infrastructure implementation within the EventBob bounded context.

## Work-in-Progress

This module is under active development. Implementation patterns and structure are evolving.
