package io.eventbob.acceptance.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Minimal, framework-agnostic HTTP client for posting an event JSON body to a running
 * microlith's {@code POST /events} endpoint and parsing the JSON response.
 *
 * <p>Deliberately framework-agnostic (plain JDK {@link HttpClient} plus a plain
 * {@link ObjectMapper}, not {@code TestRestTemplate} or a specific {@code EventDto} type) so
 * one client implementation can drive both the Dropwizard- and Spring-realized acceptance
 * test subclasses that share a base test class.
 */
public final class EventClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private EventClient() {
  }

  /**
   * Posts an event body to {@code eventsUrl} and returns the parsed JSON response.
   *
   * @param eventsUrl the full {@code /events} endpoint URL
   * @param body      the event request body (source/target/payload map)
   * @return the response status and parsed JSON body
   */
  public static Response post(String eventsUrl, Map<String, Object> body) {
    try {
      String json = OBJECT_MAPPER.writeValueAsString(body);
      HttpRequest request = HttpRequest
          .newBuilder()
          .uri(URI.create(eventsUrl))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();
      HttpResponse<String> response = HTTP_CLIENT
          .send(request, HttpResponse.BodyHandlers.ofString());
      @SuppressWarnings("unchecked")
      Map<String, Object> parsedBody = OBJECT_MAPPER.readValue(response.body(), Map.class);
      return new Response(response.statusCode(), parsedBody);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to call " + eventsUrl, e);
    } catch (InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
      throw new IllegalStateException("Interrupted while calling " + eventsUrl, e);
    }
  }

  /**
   * HTTP status code and parsed JSON body of an events-endpoint response.
   *
   * @param statusCode the HTTP status code
   * @param body       the parsed JSON response body
   */
  public record Response(int statusCode, Map<String, Object> body) {
  }
}
