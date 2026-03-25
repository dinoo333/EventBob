package io.eventbob.dropwizard.handlers;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

/**
 * Handler for healthcheck capability.
 *
 * <p>Returns an event with payload=true to indicate the service is alive and able to
 * process events. Used by monitoring systems and load balancers to verify service health.
 */
@Capability("healthcheck")
public class HealthcheckHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
        return event.toBuilder()
            .payload(true)
            .build();
    }
}
