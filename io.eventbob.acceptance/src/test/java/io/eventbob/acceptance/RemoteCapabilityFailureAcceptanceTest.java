package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.decoy.upper.DecoyUpperApplication;
import io.eventbob.acceptance.support.EventClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test closing two self-audited gaps in docs/business_use_cases.md's "As a Developer"
 * section: "Compose a capability hosted by another microlith" (Forward Event to Remote
 * Capability)'s HTTP-error and network-failure acceptance criteria, and "Have one hosted
 * capability call another" (Dispatch Between Local Capabilities)'s sync-dispatch-timeout
 * acceptance criterion - run against a real Docker container of each framework realization.
 *
 * <p>Unlike {@link EchoCapabilityAcceptanceTest}, which runs the real "upper" application (and so
 * can only ever observe a normally-resolved HTTP 200, since {@code EventBob#processEvent}'s
 * {@code exceptionally()} always converts handler exceptions into a 200-with-error-payload
 * response - see {@code EventBobTest}), this test replaces "upper" with {@link
 * DecoyUpperApplication}: a non-EventBob process that can genuinely return a non-2xx status,
 * refuse the connection, or respond slowly, letting these scenarios be exercised for real.
 *
 * <p>Each {@code @Test} method opens its own fresh, locally-scoped container (mirroring {@code
 * BootstrapMicrolithAcceptanceTest#assertAbortsStartup}, not {@link
 * EchoCapabilityAcceptanceTest}'s shared static container), because each scenario needs a
 * different decoy {@code MODE} set via {@code withEnv} before the container starts.
 *
 * <p>The HTTP-error (404/500) and network-failure (unreachable) scenarios share one assertion
 * shape: {@code EchoService#processEcho}'s "lower" dispatch still succeeds (its own leg is
 * unaffected by "upper" failing), while the "upper" leg is independently-asserted error text -
 * because the *inner* {@code dispatcher.send} call for "upper" catches the wrapped {@code
 * EventHandlingException} in its own {@code exceptionally()} and resolves normally, so {@code
 * EchoService#processEcho} completes normally and concatenates both legs' payloads. The
 * sync-timeout scenario is shaped differently: the *outer* {@code dispatcher.send} call's own
 * 1000ms {@code .get(...)} times out and throws directly, uncaught by {@code
 * EchoService#processEcho}, so the whole "echo" response - not just the "upper" half - resolves
 * as a top-level error event.
 */
@Testcontainers
public abstract class RemoteCapabilityFailureAcceptanceTest {

  /**
   * Builds a fresh echo+decoy-upper image for this framework realization.
   *
   * @return a lazily-built image
   */
  protected abstract ImageFromDockerfile image();

  /**
   * The internal port the "echo" application listens on for this framework realization.
   *
   * @return the container-internal HTTP port
   */
  protected abstract int echoPort();

  /**
   * The internal port the decoy "upper" application listens on for this framework realization.
   *
   * @return the container-internal HTTP port
   */
  protected abstract int decoyPort();

  @Test
  void remoteReturnsHttpError404_lowerSucceedsAndUpperCarriesErrorFragment() {
    assertPartialErrorResponse(DecoyUpperApplication.MODE_FIXED_404);
  }

  @Test
  void remoteReturnsHttpError500_lowerSucceedsAndUpperCarriesErrorFragment() {
    assertPartialErrorResponse(DecoyUpperApplication.MODE_FIXED_500);
  }

  @Test
  void remoteUnreachable_lowerSucceedsAndUpperCarriesErrorFragment() {
    assertPartialErrorResponse(DecoyUpperApplication.MODE_UNREACHABLE);
  }

  @Test
  void syncDispatchTimeoutExpires_wholeResponseIsErrorShaped() {
    String decoyMode = DecoyUpperApplication.MODE_DELAYED_PAST_1000MS;
    try (GenericContainer<?> container = newContainer(decoyMode)) {
      container.start();

      EventClient.Response response = EventClient.post(eventsUrl(container), Map.of(
          "source", "client",
          "target", "echo",
          "payload", "hello"));

      assertThat(response.statusCode()).isEqualTo(200);

      @SuppressWarnings("unchecked")
      Map<String, Object> errorPayload =
          (Map<String, Object>) response.body().get("payload");
      assertThat((String) errorPayload.get("errorMessage"))
          .contains("Timeout waiting for response");
      assertThat((String) errorPayload.get("errorType"))
          .contains("CompletionException");
    }
  }

  private void assertPartialErrorResponse(String decoyMode) {
    try (GenericContainer<?> container = newContainer(decoyMode)) {
      container.start();

      EventClient.Response response = EventClient.post(eventsUrl(container), Map.of(
          "source", "client",
          "target", "echo",
          "payload", "hello"));

      assertThat(response.statusCode()).isEqualTo(200);

      String payload = String.valueOf(response.body().get("payload"));
      assertThat(payload).contains("hello");
      assertThat(payload).contains("errorType");
      assertThat(payload).contains("errorMessage");
    }
  }

  /**
   * Builds a fresh, not-yet-started container for {@code decoyMode}. Waits for the decoy's port
   * too, except in {@link DecoyUpperApplication#MODE_UNREACHABLE}, whose entire point is that the
   * decoy's port is never opened - waiting for it there would hang until the wait strategy's
   * timeout on every run.
   *
   * @param decoyMode the decoy's {@code MODE} environment variable value for this scenario
   * @return a not-yet-started container configured for this scenario
   */
  private GenericContainer<?> newContainer(String decoyMode) {
    boolean decoyListens = !DecoyUpperApplication.MODE_UNREACHABLE.equals(decoyMode);
    int[] portsToAwait = decoyListens
        ? new int[] {echoPort(), decoyPort()}
        : new int[] {echoPort()};

    return new GenericContainer<>(image())
        .withEnv("MODE", decoyMode)
        .withExposedPorts(echoPort(), decoyPort())
        .waitingFor(Wait.forListeningPorts(portsToAwait));
  }

  private String eventsUrl(GenericContainer<?> container) {
    return "http://" + container.getHost() + ":" + container.getMappedPort(echoPort())
        + "/events";
  }
}
