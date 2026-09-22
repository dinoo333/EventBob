# EventBob — Business Use Cases

This is a traceability index, not a specification. It does not redefine anything — every
term, **system** use-case description, and scenario below is a pointer into the existing
canonical docs (`docs/architecture.md`, `io.eventbob.core/docs/architecture.md`,
`io.eventbob.dropwizard/docs/architecture.md`, `io.eventbob.spring/docs/architecture.md`,
and their sibling `domain_spec.md` files). Editing a business use case's linked system use
case here should mean editing the linked use-case/scenario text in `architecture.md` first,
then updating the acceptance criteria and sequence diagram to match — never writing new
**system** use-case prose here.

A business use case itself is not a system use case: it may state a business-level
requirement — such as requiring the system to remain framework-agnostic across its
`io.eventbob.spring` and `io.eventbob.dropwizard` realizations — as its own Value and
Acceptance Criteria, without that requirement being new system behavior needing its own
architecture.md use case. Such an entry still may not invent or redefine system behavior;
it may only point at existing system use cases and assert business-level requirements about
them (e.g., that they hold identically across realizations).

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
(`io.eventbob.spring`). `BootstrapMicrolithAcceptanceTest` and `ShutDownMicrolithAcceptanceTest`
(`io.eventbob.acceptance`, each with a Dropwizard and a Spring subclass) — real-Docker
Testcontainers acceptance tests covering normal startup, startup-abort-on-lifecycle-failure,
duplicate-capability-detection, ordered clean shutdown, and
shutdown-continues-after-one-holder's-error, against real container images built from the
already-packaged `io.eventbob.example.microlith.dw.echo` / `.spring.echo` fat jars (or, for the
three abort/error scenarios, a test-only "broken" image exercising the same
`EventBobBundle`/`EventBobConfig` production code with a deliberately-conflicting or
deliberately-failing lifecycle). See "As a Developer" below.

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
`InvertCapabilityAcceptanceTest` (`io.eventbob.acceptance`, with a Dropwizard and a Spring
subclass) — real-Docker Testcontainers acceptance test against the "invert" capability,
covering known-capability, unknown-capability, and handler-failure cases, against real
container images built from the already-packaged `io.eventbob.example.microlith.dw.echo` /
`.spring.echo` fat jars. See "As a Developer" below.

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
(`io.eventbob.acceptance`, with a Dropwizard and a Spring subclass) — real-Docker Testcontainers
acceptance test against the "echo" capability, which unconditionally dispatches to both this use
case's remote "upper" capability and the local "lower" capability (see "Have one hosted
capability call another" below) in one real code path, so one test covers both. The remote side
runs as a real second OS process (the already-packaged `upper` fat jar) sharing one container
with "echo", not a stub — the only way to satisfy each `EchoApplication`'s hardcoded
`localhost` remote-capability URI without changing it. See "As a Developer" below.

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
unit tests). `EchoCapabilityAcceptanceTest` (`io.eventbob.acceptance`, with a Dropwizard and a
Spring subclass) — see "Compose a capability hosted by another microlith" above; the same test
exercises this use case's local "lower" dispatch as part of the same real code path. See "As a
Developer" below.

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
(`io.eventbob.acceptance`, with a Dropwizard and a Spring subclass) — real-Docker
Testcontainers acceptance test against a running echo microlith container of each realization,
covering both payload-omitted and explicit-null-payload cases per the acceptance criteria
above. See "As a Developer" below.

---

## As a Developer

*Unlike the roles above, this is an internal actor verifying the system's own architectural
claims — not an external runtime interactor of the microlith's HTTP or process boundary.*

### Ensure business behavior is proven identical across framework realizations

**Value:** so that the "realized identically in Dropwizard and Spring" claim (already stated
above, in this document's own "Compose a capability that is actually hosted by another
microlith, transparently" entry) is backed by proof rather than assumed — business behavior
does not silently diverge between the two infrastructure realizations.

**System use cases:** [Bootstrap Microlith](../docs/architecture.md#use-case-bootstrap-microlith),
[Process Inbound Event](../docs/architecture.md#use-case-process-inbound-event),
[Forward Event to Remote Capability](../docs/architecture.md#use-case-forward-event-to-remote-capability),
[Dispatch Between Local Capabilities](../docs/architecture.md#use-case-dispatch-between-local-capabilities),
[Shut Down Microlith](../docs/architecture.md#use-case-shut-down-microlith) — all 5 existing
top-level system use cases, collectively.

**Acceptance criteria:**
- Given "Operate a microlith" (Bootstrap/Shut Down Microlith), when checked for
  Dropwizard-realized acceptance proof, then: normal startup,
  startup-abort-on-lifecycle-failure, duplicate-capability-detection, and ordered clean
  shutdown are proven, in both frameworks, by `BootstrapMicrolithAcceptanceTest` /
  `ShutDownMicrolithAcceptanceTest` (`io.eventbob.acceptance`) — real-Docker Testcontainers
  acceptance tests, run against the real `EventBobBundle` / `EventBobConfig` production code
  (the abort scenarios via a test-only "broken" image built from the same production
  bootstrap code with a deliberately-conflicting or deliberately-failing lifecycle, never a
  reimplementation of it). **Partially closed:** shutdown-continues-after-one-holder's-error
  is proven on Spring but currently fails on Dropwizard
  (`DropwizardShutDownMicrolithAcceptanceTest.oneHoldersShutdownError_shutdownContinuesForRemainingHolders`)
  — the application and Docker's own stop mechanism were independently confirmed correct via a
  standalone, non-Testcontainers diagnostic, but the same mechanism driven through
  Testcontainers/JUnit produces no shutdown-log output for unresolved reasons. Tracked as a
  known follow-up, not yet closed.
- Given "Invoke a capability over HTTP" (Process Inbound Event), when checked for
  Dropwizard-realized acceptance proof, then it is proven by `InvertCapabilityAcceptanceTest`
  (`io.eventbob.acceptance`), covering known-capability, unknown-capability, and
  handler-failure cases in both frameworks. Closed.
- Given "Compose a capability hosted by another microlith" (Forward Event to Remote
  Capability), when checked for Dropwizard-realized acceptance proof, then the happy-path
  scenario (successful forward, response converted back) is proven by
  `EchoCapabilityAcceptanceTest` (`io.eventbob.acceptance`), run against a real Docker container
  per framework in which "echo" and "upper" run as two OS processes sharing one container's
  loopback interface (the only way to satisfy each `EchoApplication`'s hardcoded `localhost`
  remote-capability URI without changing it). **Partially closed:** this business use case's
  other two acceptance criteria (remote-microlith-returns-an-HTTP-error, network-failure) are
  not exercised at acceptance level — only unit-tested via `HttpEventHandlerAdapterTest`.
  Tracked as a known follow-up.
- Given "Have one hosted capability call another" (Dispatch Between Local Capabilities), when
  checked for Dropwizard-realized acceptance proof, then the happy-path local "lower" dispatch
  leg is proven by the same `EchoCapabilityAcceptanceTest`'s second `@Test` method, asserting on
  the local-dispatch half of the same combined-response request (see "Existing test coverage"
  below for why one test covers both use cases). **Open, not closed:** this business use case's
  two acceptance criteria (async dispatch returning a future immediately, sync dispatch with
  timeout/failure semantics) are not exercised at acceptance level at all — only unit-tested via
  `DispatcherTest`. Tracked as a known follow-up.
- Given "Check that the microlith is alive" (Process Inbound Event), when checked for
  Dropwizard-realized acceptance proof, then it is proven by `HealthcheckAcceptanceTest`
  (`io.eventbob.acceptance`), covering payload-omitted and explicit-null-payload cases in both
  frameworks. Closed.

**Sequence diagram:** none new. The business-level sequence for each of the 5 use cases above
is realization-agnostic by design (location transparency and mutual exclusivity of
infrastructure libraries — see `architecture.md` §5): the flow does not change based on which
framework implements it, only the acceptance-test evidence proving it does. See the existing
sequence diagrams cited under their respective entries above:
[OperateMicrolith.mermaid](../diagrams/businessUseCases/OperateMicrolith.mermaid),
[InvokeCapabilityOverHttp.mermaid](../diagrams/businessUseCases/InvokeCapabilityOverHttp.mermaid),
[ComposeRemoteCapability.mermaid](../diagrams/businessUseCases/ComposeRemoteCapability.mermaid),
[DispatchBetweenLocalCapabilities.mermaid](../diagrams/businessUseCases/DispatchBetweenLocalCapabilities.mermaid),
[CheckMicrolithAlive.mermaid](../diagrams/businessUseCases/CheckMicrolithAlive.mermaid).

**Code:** N/A — this use case is about proof/coverage, not new production code.

**Existing test coverage:** Dropwizard-realized acceptance-test coverage now exists, via
real-Docker Testcontainers tests in the new `io.eventbob.acceptance` module, for all 5 use
cases: "Operate a microlith" (`BootstrapMicrolithAcceptanceTest`,
`ShutDownMicrolithAcceptanceTest`), "Invoke a capability over HTTP"
(`InvertCapabilityAcceptanceTest`), "Check that the microlith is alive"
(`HealthcheckAcceptanceTest`), and "Compose a capability hosted by another microlith" /
"Have one hosted capability call another" (`EchoCapabilityAcceptanceTest`) — each with a
Dropwizard and a Spring subclass sharing one abstract base test class, run against real
containers built from the already-packaged `io.eventbob.example.microlith.dw.echo` /
`.spring.echo` fat jars (or, for the bootstrap/shutdown abort/error scenarios, a test-only
"broken" image exercising the same production `EventBobBundle` / `EventBobConfig` code with a
deliberately-conflicting or deliberately-failing lifecycle; or, for `EchoCapabilityAcceptanceTest`,
a container also running the already-packaged `dw.upper` / `spring.upper` fat jar as a second OS
process sharing the same container's loopback interface). The Spring-only originals of
`InvertCapabilityAcceptanceTest`, `HealthcheckAcceptanceTest`, and `EchoCapabilityAcceptanceTest`
(previously in `io.eventbob.example.microlith.spring.echo`) were ported into
`io.eventbob.acceptance` and removed from their old location once their Testcontainers
equivalents were confirmed passing on real Docker (see the "Partially closed" / "Open, not
closed" notes above for the specific scenarios still outstanding).
