package io.eventbob.example.echo;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;

/**
 * Service that implements echo and invert processing logic.
 *
 * <p>This demonstrates extracting business logic into a separate service class
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
   * @throws EventHandlingException if dispatch fails event
   */
  public Event processEcho(Event event, Dispatcher dispatcher) throws EventHandlingException {
    Event lowerRequest = event
        .toBuilder("echo", "lower")
        .build(event.getPayload());
    Event lowerResponse = dispatcher.send(lowerRequest, (err, evt) -> null, 1000);

    Event upperRequest = event
        .toBuilder("echo", "upper")
        .build(event.getPayload());
    Event upperResponse = dispatcher.send(upperRequest, (err, evt) -> null, 1000);

    return event
        .toBuilder("echo", event.getSource())
        .build(lowerResponse.getPayload() + " " + upperResponse.getPayload());
  }

  /**
   * Processes invert request by reversing the string.
   *
   * @param event      the invert event
   * @param dispatcher the dispatcher (not used by this method)
   * @return reversed string event
   */
  public Event processInvert(Event event, Dispatcher dispatcher) {
    return event
        .toBuilder("invert", event.getSource())
        .build(new StringBuilder((String) event.getPayload())
            .reverse()
            .toString());
  }
}
