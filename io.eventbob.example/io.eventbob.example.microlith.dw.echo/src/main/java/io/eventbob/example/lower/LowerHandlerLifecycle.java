package io.eventbob.example.lower;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for LowerHandler with manual dependency injection.
 * <p>
 * Demonstrates the framework-agnostic manual wiring pattern for Dropwizard.
 * The lifecycle wires the handler using plain {@code new} operators with no
 * Spring or other container involvement.
 * </p>
 */
public class LowerHandlerLifecycle extends HandlerLifecycle {
    private LowerHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        handler = new LowerHandler(new LowerService());
    }

    @Override
    public EventHandler getHandler() {
        return handler;
    }

    @Override
    public void shutdown() {
        handler = null;
    }
}
