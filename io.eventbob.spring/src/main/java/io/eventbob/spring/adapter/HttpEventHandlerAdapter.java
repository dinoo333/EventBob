 package io.eventbob.spring.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Adapter that wraps HTTP calls to remote EventHandler endpoints.
 * <p>
 * This adapter implements the EventHandler interface but delegates actual event
 * processing to a remote service via HTTP POST. It provides location transparency:
 * from EventBob's perspective, this is just another EventHandler; the fact that
 * it makes network calls is an implementation detail.
 * </p>
 * <p>
 * Protocol:
 * </p>
 * <ul>
 *   <li>POST {remoteEndpoint}/events</li>
 *   <li>Content-Type: application/json</li>
 *   <li>Request body: serialized Event</li>
 *   <li>Response body: serialized Event</li>
 *   <li>HTTP status codes: 2xx = success, 4xx/5xx = error</li>
 * </ul>
 */
public class HttpEventHandlerAdapter implements EventHandler {
    private final URI remoteEndpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates an HTTP adapter for a remote capability endpoint.
     *
     * @param remoteEndpoint the base URI of the remote service
     * @param httpClient the HTTP client to use for requests
     */
    public HttpEventHandlerAdapter(URI remoteEndpoint, HttpClient httpClient) {
        this.remoteEndpoint = remoteEndpoint;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
        try {
            HttpRequest request = buildRequest(event);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException e) {
            throw new EventHandlingException("Network error calling remote endpoint: " + remoteEndpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventHandlingException("HTTP request interrupted: " + remoteEndpoint, e);
        }
    }

    private HttpRequest buildRequest(Event event) throws EventHandlingException {
        try {
            EventDto dto = EventDto.fromEvent(event);
            String json = objectMapper.writeValueAsString(dto);
            URI requestUri = remoteEndpoint.resolve("/events");

            return HttpRequest.newBuilder()
                    .uri(requestUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (IOException e) {
            throw new EventHandlingException("Failed to serialize event for HTTP request", e);
        }
    }

    private Event parseResponse(HttpResponse<String> response) throws EventHandlingException {
        int status = response.statusCode();

        if (status >= 400 && status < 500) {
            throw new EventHandlingException("Client error from remote endpoint (HTTP " + status + "): " + response.body());
        }

        if (status >= 500) {
            throw new EventHandlingException("Server error from remote endpoint (HTTP " + status + "): " + response.body());
        }

        try {
            EventDto dto = objectMapper.readValue(response.body(), EventDto.class);
            return dto.toEvent();
        } catch (IOException e) {
            throw new EventHandlingException("Failed to parse response from remote endpoint", e);
        }
    }
}
