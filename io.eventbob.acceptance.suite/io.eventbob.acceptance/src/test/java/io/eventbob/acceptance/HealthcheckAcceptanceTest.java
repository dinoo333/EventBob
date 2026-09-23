package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.support.EventClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for the "Check that the microlith is alive" business use case (see
 * docs/business_use_cases.md), run against a real Docker container of each framework
 * realization.
 *
 * <p>Exercises the unconditional "healthcheck" capability over real HTTP against a running
 * microlith container. Healthcheck is capability-agnostic and registered unconditionally on
 * every EventBob instance, regardless of which capabilities the hosting application itself
 * declares (echo/lower, in this case).
 */
@Testcontainers
public abstract class HealthcheckAcceptanceTest {

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
  void omittedPayload_returnsTruePayload() {
    Map<String, Object> request = Map.of(
        "source", "monitoring-system",
        "target", "healthcheck");

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body().get("payload")).isEqualTo(Boolean.TRUE);
  }

  @Test
  void explicitNullPayload_returnsTruePayload() {
    Map<String, Object> request = new HashMap<>();
    request.put("source", "monitoring-system");
    request.put("target", "healthcheck");
    request.put("payload", null);

    EventClient.Response response = EventClient.post(eventsUrl(), request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body().get("payload")).isEqualTo(Boolean.TRUE);
  }
}
