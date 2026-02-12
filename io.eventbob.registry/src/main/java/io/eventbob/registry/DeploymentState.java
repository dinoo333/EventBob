package io.eventbob.registry;

/**
 * Lifecycle states for deployments in the blue/green deployment model.
 *
 * <p>State transitions:
 * <pre>
 *   blue → green (rollout completes successfully)
 *   blue → gray  (rollout fails or is aborted)
 *   green → gray (superseded by new green)
 *   gray → retired (cleanup after grace period)
 * </pre>
 */
public enum DeploymentState {
    /**
     * New version being rolled out. Receives increasing percentage of traffic.
     * Only one blue per (service, capability, operation) at a time.
     */
    BLUE,

    /**
     * Stable production version serving traffic.
     * Only one green per (service, capability, operation) at a time.
     */
    GREEN,

    /**
     * Rolled back or superseded version. No longer receives traffic.
     * Available briefly for emergency rollback, then retired.
     */
    GRAY,

    /**
     * Decommissioned version. Registry entry remains for audit, but
     * JARs are unloaded and instances are terminated.
     */
    RETIRED
}
