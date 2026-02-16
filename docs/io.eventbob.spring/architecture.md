# io.eventbob.spring Architecture

## Module Purpose

Spring Boot infrastructure layer providing server deployment and transport adapters for EventBob.

## Layer Assignment

This module is in the **infrastructure/adapter layer**:
- Depends on `io.eventbob.core` (abstractions)
- Provides framework-specific implementations
- Core never depends on this module (dependency inversion)

## Key Components

### Application Bootstrap
- Spring Boot application entry point
- Server startup and lifecycle management

### Configuration
- Spring dependency injection wiring
- EventBob instance configuration with handlers

### Transport Adapters
- Bridge external protocols (HTTP, etc.) to EventBob Event model
- Work-in-progress

### Handlers
- Concrete EventHandler implementations
- Infrastructure-specific capabilities (e.g., healthcheck)

## Design Principles

### Bridge Pattern
- Infrastructure depends on core abstractions
- Core remains framework-agnostic
- Spring types stay in this module, never leak into core

### Dependency Flow
```
io.eventbob.spring → io.eventbob.core
(infrastructure)    (abstraction)
```

## Work-in-Progress

This module is under active development. Components and structure are evolving as requirements clarify.
