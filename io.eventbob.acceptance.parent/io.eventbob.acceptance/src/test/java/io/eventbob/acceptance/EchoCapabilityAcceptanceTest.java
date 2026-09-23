package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.support.EventClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for two business use cases that share one real code path (see
 * docs/business_use_cases.md): "Compose a capability that is actually hosted by another
 * microlith, transparently" (Forward Event to Remote Capability) and "Have one hosted capability
 * call another hosted capability during its own processing" (Dispatch Between Local
 * Capabilities), run against a real Docker container of each framework realization.
 *
 * <p>{@code EchoService.processEcho} unconditionally dispatches to the local "lower" capability
 * and the remote "upper" capability to build its combined response - there is no way to trigger
 * one leg without the other via this real production code, so both {@code @Test} methods below
 * post the identical combined-response request; each asserts on the half of the response that
 * corresponds to its own use case.
 *
 * <p>"upper" runs as a real second OS process sharing the same container as "echo" (never a
 * stub, never a second Testcontainers container): both frameworks' {@code EchoApplication}
 * hardcode the remote capability's URI to a literal {@code localhost} port, so the two
 * capabilities can only reach each other over one container's loopback interface. See {@code
 * support.Images#dwEchoAndUpper()} / {@code #springEchoAndUpper()}.
 */
@Testcontainers
public abstract class EchoCapabilityAcceptanceTest {

  /**
   * The running echo+upper container under test, supplied by the framework-specific subclass.
   *
   * @return the container to send events to
   */
  protected abstract GenericContainer<?> container();

  /**
   * The internal port the "echo" application listens on, supplied by the framework-specific
   * subclass.
   *
   * @return the container-internal HTTP port
   */
  protected abstract int echoPort();

  private String eventsUrl() {
    return "http://" + container().getHost() + ":" + container().getMappedPort(echoPort())
        + "/events";
  }

  /**
   * Covers "Compose a capability that is actually hosted by another microlith, transparently"
   * (Forward Event to Remote Capability): the "upper" leg of the combined response proves the
   * event was converted to wire format, forwarded to the real remote process, and its response
   * converted back.
   */
  @Test
  void forwardsToRemoteUpperCapability_upperCasesPayload() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "echo",
        "payload", "Hello");

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response
        .body()
        .get("payload"))
        .asString()
        .contains("HELLO");
  }

  /**
   * Covers "Have one hosted capability call another hosted capability during its own
   * processing" (Dispatch Between Local Capabilities): the "lower" leg of the combined response
   * proves the echo handler dispatched synchronously, in-process, to another local capability
   * as part of its own execution.
   */
  @Test
  void dispatchesToLocalLowerCapability_lowerCasesPayload() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "echo",
        "payload", "Hello");

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response
        .body()
        .get("payload"))
        .asString()
        .contains("hello");
  }
}
