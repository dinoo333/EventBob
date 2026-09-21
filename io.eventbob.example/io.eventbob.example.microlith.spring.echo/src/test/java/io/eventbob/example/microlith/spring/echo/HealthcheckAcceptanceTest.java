package io.eventbob.example.microlith.spring.echo;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.spring.adapter.EventDto;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Acceptance test for the "Check that the microlith is alive" business use case
 * (see docs/business_use_cases.md).
 *
 * <p>Exercises the unconditional "healthcheck" capability over real HTTP against a running
 * {@link EchoApplication} instance. {@code EchoApplication} is used here as a host of
 * convenience — the healthcheck capability is capability-agnostic and registered
 * unconditionally on every EventBob instance by {@code EventBobConfig}, regardless of which
 * capabilities the hosting application itself declares (echo/lower, in this case). This test
 * does not verify anything about the echo or lower capabilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthcheckAcceptanceTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void omittedPayload_returnsTruePayload() {
    Map<String, Object> request = Map.of(
        "source", "monitoring-system",
        "target", "healthcheck");

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().payload()).isEqualTo(Boolean.TRUE);
  }

  @Test
  void explicitNullPayload_returnsTruePayload() {
    Map<String, Object> request = new HashMap<>();
    request.put("source", "monitoring-system");
    request.put("target", "healthcheck");
    request.put("payload", null);

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().payload()).isEqualTo(Boolean.TRUE);
  }
}
