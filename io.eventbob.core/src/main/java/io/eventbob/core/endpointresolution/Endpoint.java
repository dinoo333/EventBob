package io.eventbob.core.endpointresolution;

/**
 * Physical endpoint where events can be sent.
 *
 * <p>An endpoint is simply a network address where a service can receive events.
 *
 * @param url the endpoint URL (e.g., "http://10.0.1.5:8080")
 */
public record Endpoint(String url) {
    public Endpoint {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
    }
}
