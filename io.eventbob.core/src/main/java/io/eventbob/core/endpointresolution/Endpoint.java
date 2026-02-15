package io.eventbob.core.endpointresolution;

/**
 * Logical endpoint where events can be sent.
 *
 * <p>An endpoint is a logical service URL that cloud infrastructure resolves to physical addresses.
 * The infrastructure (DNS, service mesh, load balancers) handles the mapping from logical URL to
 * actual server instances.
 *
 * @param url the logical endpoint URL (e.g., "http://messages-service")
 */
public record Endpoint(String url) {
    public Endpoint {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
    }
}
