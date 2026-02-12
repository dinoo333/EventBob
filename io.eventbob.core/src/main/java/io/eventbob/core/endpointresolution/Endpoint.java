package io.eventbob.core.endpointresolution;

/**
 * Physical endpoint where events can be sent.
 *
 * <p>This is a domain concept - where the router should send the event.
 *
 * @param url                the endpoint URL (e.g., "http://10.0.1.5:8080")
 * @param deploymentVersion  deployment version number for observability
 * @param state              endpoint state (GREEN or BLUE) for progressive routing
 */
public record Endpoint(
    String url,
    int deploymentVersion,
    EndpointState state
) {
    public Endpoint {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (deploymentVersion <= 0) {
            throw new IllegalArgumentException("deploymentVersion must be positive");
        }
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
    }
}
