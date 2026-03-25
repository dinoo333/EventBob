package io.eventbob.example.echo;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for EchoHandler with manual dependency injection.
 * <p>
 * Demonstrates the framework-agnostic manual wiring pattern for Dropwizard.
 * The lifecycle wires the handler using plain {@code new} operators with no
 * Spring or other container involvement.
 * </p>
 */
public class EchoHandlerLifecycle extends HandlerLifecycle {
    private EchoHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        handler = new EchoHandler(new EchoService());
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
