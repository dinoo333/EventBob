package io.eventbob.spring.adapter;

import java.net.URI;

/**
 * Represents a remote capability endpoint for inter-microlith communication.
 * <p>
 * A RemoteCapability maps a capability name (e.g., "upper", "email") to a remote
 * endpoint URI. This enables EventBob to route events to handlers running in
 * different processes or services.
 * </p>
 * <p>
 * The transport mechanism (HTTP, gRPC, JMS, etc.) is determined by the URI scheme
 * and is handled by the appropriate adapter implementation (e.g., HttpEventHandlerAdapter
 * for http/https URIs).
 * </p>
 *
 * @param name the capability name (must not be null or blank)
 * @param uri the remote endpoint URI (must not be null)
 */
public record RemoteCapability(String name, URI uri) {
    /**
     * Creates a remote capability record with validation.
     *
     * @param name the capability name
     * @param uri the remote endpoint URI
     * @throws IllegalArgumentException if name is null/blank or uri is null
     */
    public RemoteCapability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Capability name must not be null or blank");
        }
        if (uri == null) {
            throw new IllegalArgumentException("URI must not be null");
        }
    }
}
