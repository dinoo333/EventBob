package io.eventbob.example.microlith.spring.echo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.eventbob.spring.adapter.EventDto;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Acceptance test for two business use cases that share one real code path (see
 * docs/business_use_cases.md): "Compose a capability that is actually hosted by another
 * microlith, transparently" and "Have one hosted capability call another hosted capability
 * during its own processing".
 *
 * <p>{@code EchoService.processEcho} unconditionally dispatches to the local "lower" capability
 * and the remote "upper" capability to build its combined response — there is no way to
 * exercise one without the other via this real production code, so a single test of the
 * "echo" capability verifies both use cases at once.
 *
 * <p>The "upper" remote capability is stubbed with a plain JDK {@link HttpServer} bound to the
 * literal port {@code EchoApplication}'s hardcoded {@code RemoteCapability} bean expects
 * ({@code http://localhost:8081}), rather than a real {@code UpperApplication} instance. This
 * verifies Echo's dispatch/combine wiring and the real wire protocol faithfully; {@code upper}'s
 * own uppercase logic already has direct unit coverage in {@code UpperHandlerTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EchoCapabilityAcceptanceTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static HttpServer upperStub;

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @BeforeAll
  @SuppressWarnings("unchecked")
  static void startUpperStub() throws IOException {
    upperStub = HttpServer.create(new InetSocketAddress(8081), 0);
    upperStub.createContext("/events", exchange -> {
      Map<String, Object> request = OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class);
      String payload = (String) request.get("payload");
      String responseBody = OBJECT_MAPPER.writeValueAsString(Map.of(
          "source", "upper",
          "target", "client",
          "payload", payload.toUpperCase()));
      byte[] responseBytes = responseBody.getBytes();
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, responseBytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(responseBytes);
      }
    });
    upperStub.start();
  }

  @AfterAll
  static void stopUpperStub() {
    upperStub.stop(0);
  }

  @Test
  void echoCapability_combinesLocalAndRemoteDispatchResults() {
    Map<String, Object> request = Map.of(
        "source", "client",
        "target", "echo",
        "payload", "Hello");

    ResponseEntity<EventDto> response = restTemplate.postForEntity(
        "http://localhost:" + port + "/events", request, EventDto.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().payload()).isEqualTo("hello HELLO");
  }
}
