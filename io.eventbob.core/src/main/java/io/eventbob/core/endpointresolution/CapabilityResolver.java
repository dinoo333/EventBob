package io.eventbob.core.endpointresolution;

import java.util.List;
import java.util.Optional;

/**
 * Resolves service capabilities to physical endpoints.
 *
 * <p>This is the abstraction (port) that defines what routing needs.
 * Implementation modules (Spring, Dropwizard) provide concrete implementations.
 *
 * <p>The router uses this interface to find where to send events, without
 * knowing HOW the resolution works (database, in-memory, remote registry, etc.).
 */
public interface CapabilityResolver {

    /**
     * Resolve a service operation to a single physical endpoint.
     *
     * @param key routing key identifying the operation
     * @return endpoint if found, empty if no handler available
     */
    Optional<Endpoint> resolve(RoutingKey key);

    /**
     * Resolve to all available endpoints for load balancing.
     *
     * <p>Returns multiple endpoints when:
     * <ul>
     *   <li>Multiple instances provide the capability (scale-out)</li>
     *   <li>Progressive rollout (both GREEN and BLUE endpoints)</li>
     * </ul>
     *
     * @param key routing key identifying the operation
     * @return list of endpoints (empty if no handlers available)
     */
    List<Endpoint> resolveAll(RoutingKey key);
}
