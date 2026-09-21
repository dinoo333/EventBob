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
 * Acceptance test for the "Invoke a capability over HTTP and get a result" business use case
 * (see docs/business_use_cases.md).
 *
 * <p>Exercises the "invert" capability, a local, no-dispatch capability natively owned by
 * {@link EchoApplication}, over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvertCapabilityAcceptanceTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void knownCapability_returnsReversedPayload() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "invert",
        "payload", "hello");

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().payload()).isEqualTo("olleh");
  }

  @Test
  void unknownCapability_returnsErrorEvent() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "does-not-exist");

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) response.getBody().payload();
    assertThat(errorPayload).containsEntry("errorType", "java.util.concurrent.CompletionException");
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

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) response.getBody().payload();
    assertThat(errorPayload).containsEntry("errorType", "java.util.concurrent.CompletionException");
    assertThat((String) errorPayload.get("errorMessage")).contains("ClassCastException");
  }
}
