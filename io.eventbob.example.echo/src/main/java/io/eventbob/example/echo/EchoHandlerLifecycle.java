package io.eventbob.example.echo;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle implementation for EchoHandler.
 * <p>
 * Demonstrates dependency injection pattern. The lifecycle creates {@link EchoService}
 * and wires it to {@link EchoHandler} via constructor injection.
 * </p>
 * <p>
 * This pattern allows handlers to depend on services, repositories, HTTP clients, etc.,
 * while keeping the handler itself framework-agnostic. The lifecycle is responsible for
 * creating and wiring dependencies.
 * </p>
 */
public class EchoHandlerLifecycle extends HandlerLifecycle {
    private EchoHandler handler;

    @Override
    public void initialize(LifecycleContext context) {
        // Create service with dispatcher from context
        EchoService echoService = new EchoService(context.getDispatcher());
        // Wire service to handler via constructor injection
        this.handler = new EchoHandler(echoService);
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
