package io.eventbob.core.endpointresolution;

/**
 * Routing key uniquely identifies a service operation for capability resolution.
 *
 * <p>This is a domain concept - what the router needs to know to find a handler.
 *
 * @param serviceName the target service
 * @param capability  what kind of operation (READ, WRITE, ADMIN)
 * @param method      operation method (GET, POST, etc.)
 * @param path        operation path pattern (/content, /user/{id})
 */
public record RoutingKey(
    String serviceName,
    Capability capability,
    String method,
    String path
) {
    public RoutingKey {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName is required");
        }
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method is required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
    }
}
