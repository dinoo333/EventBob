package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.support.EventClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for the "Invoke a capability over HTTP and get a result" business use case
 * (see docs/business_use_cases.md), run against a real Docker container of each framework
 * realization.
 *
 * <p>Exercises the "invert" capability, a local, no-dispatch capability natively owned by the
 * echo microlith, over real HTTP.
 */
@Testcontainers
public abstract class InvertCapabilityAcceptanceTest {

  /**
   * The running microlith container under test, supplied by the framework-specific subclass.
   *
   * @return the container to send events to
   */
  protected abstract GenericContainer<?> container();

  /**
   * The internal port the microlith listens on, supplied by the framework-specific subclass.
   *
   * @return the container-internal HTTP port
   */
  protected abstract int port();

  private String eventsUrl() {
    return "http://" + container().getHost() + ":" + container().getMappedPort(port())
        + "/events";
  }

  @Test
  void knownCapability_returnsReversedPayload() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "invert",
        "payload", "hello");

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body().get("payload")).isEqualTo("olleh");
  }

  @Test
  void unknownCapability_returnsErrorEvent() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "does-not-exist");

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) response.body().get("payload");
    assertThat(errorPayload)
        .containsEntry("errorType", "java.util.concurrent.CompletionException");
    assertThat((String) errorPayload.get("errorMessage"))
        .contains("HandlerNotFoundException")
        .contains("does-not-exist");
  }

  @Test
  void handlerFailure_returnsErrorEvent() {
    Map<String, Object> request = new HashMap<>();
    request.put("source", "client");
    request.put("target", "invert");
    request.put("payload", 12345);

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) response.body().get("payload");
    assertThat(errorPayload)
        .containsEntry("errorType", "java.util.concurrent.CompletionException");
    assertThat((String) errorPayload.get("errorMessage")).contains("ClassCastException");
  }
}
