package io.eventbob.core.endpointresolution;

/**
 * Capability types that event handlers can provide.
 *
 * <p>Capabilities represent the kind of operations a service can perform:
 * <ul>
 *   <li>READ - Query operations (GET, HEAD, OPTIONS)</li>
 *   <li>WRITE - Mutation operations (POST, PUT, PATCH, DELETE)</li>
 *   <li>ADMIN - Administrative operations (migrations, cache purges)</li>
 * </ul>
 */
public enum Capability {
    READ,
    WRITE,
    ADMIN;

    /**
     * Infer capability from HTTP method.
     */
    public static Capability fromMethod(String method) {
        return switch (method.toUpperCase()) {
            case "GET", "HEAD", "OPTIONS" -> READ;
            case "POST", "PUT", "PATCH", "DELETE" -> WRITE;
            default -> throw new IllegalArgumentException("Cannot infer capability from method: " + method);
        };
    }
}
