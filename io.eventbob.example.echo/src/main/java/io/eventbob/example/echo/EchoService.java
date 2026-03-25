package io.eventbob.example.echo;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;

/**
 * Service that implements echo and invert processing logic.
 * <p>
 * This demonstrates extracting business logic into a separate service class
 * that can be wired into the handler via lifecycle. In real applications, this
 * would be a service with database access, HTTP clients, or other dependencies.
 * </p>
 */
public class EchoService {
    /**
     * Processes echo request by calling lower and upper capabilities.
     *
     * @param event the echo event
     * @return combined result from lower and upper
     * @throws EventHandlingException if dispatch fails
     */
    public String processEcho(Event event, Dispatcher dispatcher) throws EventHandlingException {
        Event lowerRequest = Event.builder()
            .source("echo")
            .target("lower")
            .payload(event.getPayload())
            .build();
        Event lowerResponse = dispatcher.send(lowerRequest, (err, evt) -> null, 1000);

        Event upperRequest = Event.builder()
            .source("echo")
            .target("upper")
            .payload(event.getPayload())
            .build();
        Event upperResponse = dispatcher.send(upperRequest, (err, evt) -> null, 1000);

        return lowerResponse.getPayload() + " " + upperResponse.getPayload();
    }

    /**
     * Processes invert request by reversing the string.
     *
     * @param input the string to reverse
     * @param dispatcher the dispatcher (not used by this method)
     * @return reversed string
     */
    public String processInvert(String input, Dispatcher dispatcher) {
        return new StringBuilder(input).reverse().toString();
    }
}
