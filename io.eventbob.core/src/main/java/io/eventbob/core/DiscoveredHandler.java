package io.eventbob.core;

/**
 * Represents a discovered event handler with its associated capability.
 * <p>
 * This record captures the result of scanning a JAR file for handler implementations.
 * Each discovered handler consists of the capability name (extracted from the @Capability
 * annotation) and the handler class itself.
 * </p>
 * <p>
 * This is an internal data structure used during handler discovery and should not be
 * exposed outside the core package.
 * </p>
 *
 * @param capability the capability name this handler provides
 * @param handlerClass the EventHandler implementation class
 */
record DiscoveredHandler(
    String capability,
    Class<? extends EventHandler> handlerClass
) {
    /**
     * Creates a discovered handler record.
     *
     * @param capability the capability name (must not be null or blank)
     * @param handlerClass the handler class (must not be null)
     * @throws IllegalArgumentException if capability is null/blank or handlerClass is null
     */
    DiscoveredHandler {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("Capability must not be null or blank");
        }
        if (handlerClass == null) {
            throw new IllegalArgumentException("Handler class must not be null");
        }
    }
}
