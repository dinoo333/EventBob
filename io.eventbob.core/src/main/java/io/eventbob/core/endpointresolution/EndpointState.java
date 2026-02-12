package io.eventbob.core.endpointresolution;

/**
 * Endpoint state for progressive rollout routing.
 *
 * <p>This is a domain concept visible to the router for traffic splitting:
 * <ul>
 *   <li>GREEN - Stable production endpoint (receives baseline traffic)</li>
 *   <li>BLUE - New endpoint being rolled out (receives increasing traffic)</li>
 * </ul>
 *
 * <p>Note: GRAY and RETIRED states are infrastructure concerns (handled by
 * the registry implementation) and are never returned to the router.
 */
public enum EndpointState {
    /**
     * Stable production endpoint.
     */
    GREEN,

    /**
     * New endpoint being progressively rolled out.
     */
    BLUE
}
