# EventBob — Business Use Cases

This is a traceability index, not a specification. It does not redefine anything — every
term, use-case description, and scenario below is a pointer into the existing canonical
docs (`docs/architecture.md`, `io.eventbob.core/docs/architecture.md`,
`io.eventbob.dropwizard/docs/architecture.md`, `io.eventbob.spring/docs/architecture.md`,
and their sibling `domain_spec.md` files). Editing a business use case here should mean
editing the linked use-case/scenario text in `architecture.md` first, then updating the
acceptance criteria and sequence diagram to match — never writing new use-case prose here.

Structure per business use case: **Role → Business Use Case (+ value, linked to
`architecture.md`) → Acceptance Criteria → Sequence Diagram (code/test citations).**

> **Status:** Sequence diagrams (layer 4) are now built under `diagrams/businessUseCases/`,
> each with `alt`/`else` branches matching the acceptance criteria below one-for-one.

---

## As an Operator

### Operate a microlith as a running service

**Value:** so that capabilities become available/unavailable without resource leaks or
half-initialized state.

**System use cases:** [Bootstrap Microlith](../docs/architecture.md#use-case-bootstrap-microlith),
[Shut Down Microlith](../docs/architecture.md#use-case-shut-down-microlith) (top-level);
refined per-realization as Assemble Microlith / Shut Down Library in
[Dropwizard](../io.eventbob.dropwizard/docs/architecture.md#4-use-cases) and
[Spring](../io.eventbob.spring/docs/architecture.md#4-use-cases).

**Acceptance criteria:**
- Given handler sources are available, when the microlith starts, then all local and remote
  capabilities are registered, the healthcheck is registered unconditionally, and the server
  begins accepting requests.
- Given a lifecycle holder fails to initialize, when the microlith starts, then startup
  aborts and the process exits without accepting requests.
- Given two handler sources declare the same capability name, when the microlith starts,
  then the conflict is detected before the router is built and startup aborts.
- Given a running microlith, when it receives a stop signal, then in-flight handler
  executions complete before lifecycle holders shut down in registration order and
  resources are released.
- Given one lifecycle holder's shutdown raises an error, when shutdown proceeds, then the
  error is logged and shutdown continues for the remaining holders.

**Sequence diagram:** [OperateMicrolith.mermaid](../diagrams/businessUseCases/OperateMicrolith.mermaid)

**Code:** `io.eventbob.core.EventBob` (build/close), `io.eventbob.core.HandlerLoader` and its
JAR/lifecycle implementations, `io.eventbob.dropwizard.EventBobBundle` /
`io.eventbob.spring.EventBobConfig` (source aggregation, duplicate detection).

**Existing test coverage:** `EventBobTest`, `JarHandlerLoaderTest`, `LifecycleHandlerLoaderTest`
(`io.eventbob.core`); `EventBobBundleTest` (`io.eventbob.dropwizard`); `EventBobConfigTest`
(`io.eventbob.spring`). No dedicated acceptance test exists yet for this business use case.

---

## As an HTTP Client

### Invoke a capability over HTTP and get a result

**Value:** so that I can invoke business capabilities hosted by the microlith through one
consistent interface.

**System use cases:** [Process Inbound Event](../docs/architecture.md#use-case-process-inbound-event)
(top-level), [Route Inbound Event](../io.eventbob.core/docs/architecture.md#use-case-route-inbound-event)
(core), [Delegate to Capability](../io.eventbob.core/docs/architecture.md#use-case-delegate-to-capability),
realized as Process Inbound HTTP Event in
[Dropwizard](../io.eventbob.dropwizard/docs/architecture.md#4-use-cases) /
[Spring](../io.eventbob.spring/docs/architecture.md#4-use-cases).

**Acceptance criteria:**
- Given a known local capability, when an event is posted, then it is routed in-process to
  the handler and the result is returned.
- Given an unknown capability, when an event is posted, then the error callback is applied
  and an error event is returned (HTTP 200 with an error-shaped payload — confirmed
  intended, not a gap).
- Given a handler raises a failure, when processing the event, then the error callback is
  applied and the error event is returned.

**Sequence diagram:** [InvokeCapabilityOverHttp.mermaid](../diagrams/businessUseCases/InvokeCapabilityOverHttp.mermaid)

**Code:** `io.eventbob.core.EventBob#processEvent`, `io.eventbob.dropwizard.EventResource` /
`io.eventbob.spring` inbound controller, `EventDto`.

**Existing test coverage:** `EventBobTest` (`io.eventbob.core`, handler-level unit tests).
`InvertCapabilityAcceptanceTest` (`io.eventbob.example.microlith.spring.echo`) — real-HTTP
acceptance test against the "invert" capability, covering known-capability, unknown-capability,
and handler-failure cases.

---

### Compose a capability that is actually hosted by another microlith, transparently

**Value:** so that a capability call is transparently forwarded when it's hosted elsewhere —
this is EventBob's core "eliminate chatty calls" value proposition.

**System use cases:** [Forward Event to Remote Capability](../docs/architecture.md#use-case-forward-event-to-remote-capability)
(top-level), realized identically in
[Dropwizard](../io.eventbob.dropwizard/docs/architecture.md#4-use-cases) and
[Spring](../io.eventbob.spring/docs/architecture.md#4-use-cases). Depends on Bootstrap/Assemble
having registered the remote capability first (see "Operate a microlith" above).

**Acceptance criteria:**
- Given the remote microlith is available, when an event targets a remote capability, then
  it is converted to wire format, posted to the remote endpoint, and the response is
  converted back and returned.
- Given the remote microlith returns an HTTP error, when forwarding, then a handling failure
  is surfaced and the error callback is applied.
- Given a network failure occurs, when forwarding, then the transport exception is wrapped
  as a handling failure, restoring the interrupt flag if interrupted.

**Sequence diagram:** [ComposeRemoteCapability.mermaid](../diagrams/businessUseCases/ComposeRemoteCapability.mermaid)

**Code:** `HttpEventHandlerAdapter` and `RemoteHandlerLoader` (`io.eventbob.dropwizard` and
`io.eventbob.spring`, each depending on core's `SyncForwardingEventHandler`), `RemoteCapability`.

**Existing test coverage:** `HttpEventHandlerAdapterTest`, `RemoteHandlerLoaderTest`
(both `io.eventbob.dropwizard` and `io.eventbob.spring`); `SyncForwardingEventHandlerTest`
(`io.eventbob.core`) — handler-level unit tests. `EchoCapabilityAcceptanceTest`
(`io.eventbob.example.microlith.spring.echo`) — real-HTTP acceptance test against the "echo"
capability, which unconditionally dispatches to both this use case's remote "upper" capability
and the local "lower" capability (see "Have one hosted capability call another" below) in one
real code path, so one test covers both. The remote side is stubbed with a plain JDK
`HttpServer` on the literal port `EchoApplication`'s `RemoteCapability` bean expects, rather
than a real `UpperApplication` instance — `upper`'s own logic already has direct coverage in
`UpperHandlerTest`.

---

### Have one hosted capability call another hosted capability during its own processing

**Value:** so that a single request can produce a composed, multi-step response without the
client making multiple calls.

**System use cases:** [Dispatch Between Local Capabilities](../docs/architecture.md#use-case-dispatch-between-local-capabilities)
(top-level), [Delegate to Capability](../io.eventbob.core/docs/architecture.md#use-case-delegate-to-capability)
(core).

**Acceptance criteria:**
- Given a handler dispatches asynchronously, when it calls another capability, then a future
  is returned immediately and the caller controls the timeout.
- Given a handler dispatches synchronously with a timeout, when it calls another capability,
  then execution blocks until the result is available or the timeout expires, raising a
  handling failure on timeout, interruption, or handler failure.

**Sequence diagram:** [DispatchBetweenLocalCapabilities.mermaid](../diagrams/businessUseCases/DispatchBetweenLocalCapabilities.mermaid)

**Code:** `io.eventbob.core.Dispatcher` (`send` async/sync variants), `io.eventbob.core.EventBob`
(dispatcher exposure).

**Existing test coverage:** `DispatcherTest`, `EventBobTest` (`io.eventbob.core`, handler-level
unit tests). `EchoCapabilityAcceptanceTest` (`io.eventbob.example.microlith.spring.echo`) —
see "Compose a capability hosted by another microlith" above; the same test exercises this
use case's local "lower" dispatch as part of the same real code path.

---

## As a Monitoring System / Load Balancer

### Check that the microlith is alive

**Value:** so that I can determine whether the microlith is available.

**System use cases:** [Process Inbound Event](../docs/architecture.md#use-case-process-inbound-event)
against the unconditional, un-overridable "healthcheck" capability, realized as the "Provide
Healthcheck" interactor documented under
[Dropwizard](../io.eventbob.dropwizard/docs/architecture.md#border-dropwizard-infrastructure-library) /
[Spring](../io.eventbob.spring/docs/architecture.md#border-spring-infrastructure-library).

**Acceptance criteria:**
- Given the microlith is running, when a healthcheck event is posted (payload omitted or
  explicit null), then the response payload is `true` (live-verified in the top-level
  README's Dropwizard walkthrough).

**Sequence diagram:** [CheckMicrolithAlive.mermaid](../diagrams/businessUseCases/CheckMicrolithAlive.mermaid)

**Code:** `io.eventbob.dropwizard.handlers.HealthcheckHandler` /
`io.eventbob.spring.handlers.HealthcheckHandler`.

**Existing test coverage:** `HealthcheckHandlerTest` (both `io.eventbob.dropwizard` and
`io.eventbob.spring`, handler-level unit tests). `HealthcheckAcceptanceTest`
(`io.eventbob.example.microlith.spring.echo`) — real-HTTP acceptance test against a running
`EchoApplication` instance, covering both payload-omitted and explicit-null-payload cases per
the acceptance criteria above. No Dropwizard-realized acceptance test exists yet
(`io.eventbob.dropwizard` has no `dropwizard-testing` dependency — tracked as a known
follow-up, not part of this test).
