package io.eventbob.registry;

/**
 * Health status of a service instance.
 */
public enum InstanceStatus {
    /**
     * Instance is healthy and receiving traffic.
     */
    HEALTHY,

    /**
     * Instance is unhealthy (failed health checks). No traffic routed.
     */
    UNHEALTHY,

    /**
     * Instance is draining connections before shutdown. No new traffic.
     */
    DRAINING,

    /**
     * Instance has been terminated.
     */
    TERMINATED
}
