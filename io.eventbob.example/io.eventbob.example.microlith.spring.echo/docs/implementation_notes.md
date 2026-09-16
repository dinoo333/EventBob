# io.eventbob.example.microlith.spring.echo — Implementation Notes

### 1 Frameworks used

- **Spring (`AnnotationConfigApplicationContext`)**: Used inside `EchoHandlerLifecycle` and `LowerHandlerLifecycle` to create per-handler isolated Spring application contexts. Each lifecycle class creates its own `AnnotationConfigApplicationContext`, registers an inner `@Configuration` class, calls `refresh()`, and retrieves the handler bean. The handler and service classes themselves carry no Spring annotations.

### 2 Coding patterns

- **Per-handler isolated Spring context**: Each lifecycle (`EchoHandlerLifecycle`, `LowerHandlerLifecycle`) creates its own `AnnotationConfigApplicationContext` inside `initialize()`. The inner `@Configuration` class (`EchoHandlerConfiguration`, `LowerHandlerConfiguration`) is a static nested class within the lifecycle. This prevents bean pollution between handlers: neither lifecycle sees the other's beans, and neither sees the microlith's main `SpringApplication` context. The handler and service POJOs contain no Spring annotations.

- **`try/finally` context leak guard**: Both lifecycle `initialize()` methods use a `boolean success` flag with `try/finally` to guarantee the temporary `ctx` is closed if `refresh()` throws. Without this, a failed Spring context refresh would leave file handles, executor threads, and other Spring-managed resources open.

- **Compile-scope handler module dependency**: `EchoHandlerLifecycle` and `LowerHandlerLifecycle` are instantiated directly with `new` in `EchoApplication` bean methods. This requires compile-scope Maven dependencies on the handler modules (`io.eventbob.example.echo`, `io.eventbob.example.lower`). This is distinct from the JAR-loading pattern, which uses file-path references and runtime isolation.

- **`upper` kept remote to demonstrate mixed topology**: The `remoteCapabilities()` bean deliberately delegates `upper` to `http://localhost:8081` rather than providing an inline lifecycle for it. This is not accidental; the module exists to demonstrate that inline and remote capabilities coexist in a single `EventBobConfig`.

### 3 Coding problems

- **Remote endpoint is hard-coded**: `URI.create("http://localhost:8081")` in `EchoApplication.remoteCapabilities()` is a hard-coded constant. There is no property-based externalisation. Running more than one microlith instance on the same machine requires source changes. Status: open.

- **No automated tests**: The module has no test sources. Integration tests that start the application and verify capability responses have not been written. Status: open.

- **`upper` remote endpoint assumed to be running**: If the remote microlith at `http://localhost:8081` is not available, any request targeting `upper` fails at invocation time with a network error, not at startup. There is no connection pre-check or fail-fast validation. Status: open.

### 4 Release notes

- 2026-03-24 / dino: Replaced JAR-loading approach with inline lifecycle wiring; `EchoHandlerLifecycle` and `LowerHandlerLifecycle` declared as `@Bean` methods in `EchoApplication`; `upper` kept as remote capability.
- 2026-03-24 / dino: Module renamed and restructured after collapse of `-spring` handler modules into microliths.
- 2026-02-17 / dino: Initial microlith with JAR-based handler loading for `echo` and `lower`; `upper` remote.

### 5 AI Invariants: correctness, precision, testability

- `EchoHandlerLifecycle.initialize()` and `LowerHandlerLifecycle.initialize()` are `synchronized`; they must not be called concurrently. `EventBobConfig` calls them sequentially during the `@Bean eventBob(...)` construction phase, so no concurrent call should occur in practice.
- The `volatile` fields `handler` and `applicationContext` in both lifecycle classes ensure that a thread reading `getHandler()` outside a `synchronized` block sees the value written by `initialize()`. Removing `volatile` from either field would introduce a data race.
- Both lifecycle `shutdown()` methods null out `handler` and `applicationContext` after closing the context. Any call to `getHandler()` after `shutdown()` returns `null`; callers must not invoke the returned handler after shutdown.
- Each lifecycle bean method in the application configuration is a distinct bean definition; `EventBobConfig` receives each lifecycle instance exactly once via list injection and initialises it exactly once.
- The inner `@Configuration` classes (`EchoHandlerConfiguration`, `LowerHandlerConfiguration`) are `static` nested classes. They must remain `static` to be usable as standalone configuration sources in `AnnotationConfigApplicationContext.register()`; non-static inner classes cannot be registered this way.
