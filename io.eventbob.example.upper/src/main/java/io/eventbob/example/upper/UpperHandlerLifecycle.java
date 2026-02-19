package io.eventbob.example.upper;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for UpperHandler.
 * <p>
 * Demonstrates dependency injection pattern. The lifecycle creates {@link UpperService}
 * and wires it to {@link UpperHandler} via constructor injection.
 * </p>
 */
public class UpperHandlerLifecycle extends HandlerLifecycle {
    private UpperHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        // Create service with dispatcher from context
        UpperService upperService = new UpperService(context.getDispatcher());
        // Wire service to handler via constructor injection
        this.handler = new UpperHandler(upperService);
    }

    @Override
    public EventHandler getHandler() {
        return handler;
    }

    @Override
    public void shutdown() {
        // No resources to clean up
    }
}
