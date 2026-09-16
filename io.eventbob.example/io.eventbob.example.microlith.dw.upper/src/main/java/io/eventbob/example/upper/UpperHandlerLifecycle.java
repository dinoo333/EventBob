package io.eventbob.example.upper;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for UpperHandler with manual dependency injection.
 * <p>
 * Demonstrates the framework-agnostic manual wiring pattern for Dropwizard.
 * The lifecycle wires the handler using plain {@code new} operators with no
 * Spring or other container involvement.
 * </p>
 */
public class UpperHandlerLifecycle extends HandlerLifecycle {
    private UpperHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        handler = new UpperHandler(new UpperService());
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
