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
    private final Dispatcher dispatcher;

    /**
     * Creates an echo service with the specified dispatcher.
     *
     * @param dispatcher dispatcher for calling other capabilities
     */
    public EchoService(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Processes echo request by calling lower and upper capabilities.
     *
     * @param event the echo event
     * @return combined result from lower and upper
     * @throws EventHandlingException if dispatch fails
     */
    public String processEcho(Event event) throws EventHandlingException {
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
     * @return reversed string
     */
    public String processInvert(String input) {
        return new StringBuilder(input).reverse().toString();
    }
}
