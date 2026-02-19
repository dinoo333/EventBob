package io.eventbob.example.lower;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for LowerHandler.
 * <p>
 * Demonstrates dependency injection pattern. The lifecycle creates {@link LowerService}
 * and wires it to {@link LowerHandler} via constructor injection.
 * </p>
 */
public class LowerHandlerLifecycle extends HandlerLifecycle {
    private LowerHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        // Create service with dispatcher from context
        LowerService lowerService = new LowerService(context.getDispatcher());
        // Wire service to handler via constructor injection
        this.handler = new LowerHandler(lowerService);
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
