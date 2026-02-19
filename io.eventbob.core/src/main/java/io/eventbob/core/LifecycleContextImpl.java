package io.eventbob.core;

import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of LifecycleContext.
 * <p>
 * Provides handler configuration, dispatcher, and optional framework context
 * to handlers during initialization.
 * </p>
 */
class LifecycleContextImpl implements LifecycleContext {
    private final Map<String, Object> configuration;
    private final Dispatcher dispatcher;
    private final Object frameworkContext;

    /**
     * Creates a lifecycle context with the specified configuration and dispatcher.
     *
     * @param configuration handler-specific configuration (from application.yml)
     * @param dispatcher the dispatcher for this microlith
     */
    LifecycleContextImpl(Map<String, Object> configuration, Dispatcher dispatcher) {
        this(configuration, dispatcher, null);
    }

    /**
     * Creates a lifecycle context with configuration, dispatcher, and framework context.
     *
     * @param configuration handler-specific configuration (from application.yml)
     * @param dispatcher the dispatcher for this microlith
     * @param frameworkContext optional framework-specific context (Spring ApplicationContext, etc.)
     */
    LifecycleContextImpl(Map<String, Object> configuration, Dispatcher dispatcher, Object frameworkContext) {
        this.configuration = configuration != null ? configuration : Map.of();
        this.dispatcher = dispatcher;
        this.frameworkContext = frameworkContext;
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    @Override
    public <T> Optional<T> getFrameworkContext(Class<T> type) {
        if (frameworkContext != null && type.isInstance(frameworkContext)) {
            return Optional.of(type.cast(frameworkContext));
        }
        return Optional.empty();
    }
}
