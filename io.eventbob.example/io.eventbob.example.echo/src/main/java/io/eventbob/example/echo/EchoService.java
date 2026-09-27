package io.eventbob.example.echo;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventBuilder;
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
   * @param event        the echo event
   * @param eventBuilder the event builder
   * @return combined result from lower and upper
   * @throws EventHandlingException if dispatch fails event
   */
  public Event processEcho(Event event, EventBuilder eventBuilder, Dispatcher dispatcher)
      throws EventHandlingException {
    Event lowerRequest = eventBuilder.build(event, "echo", "lower", event.getPayload());
    Event lowerResponse = dispatcher.send(lowerRequest, (err, evt) -> null, 1000);

    Event upperRequest = eventBuilder.build(event, "echo", "upper", event.getPayload());
    Event upperResponse = dispatcher.send(upperRequest, (err, evt) -> null, 1000);

    return eventBuilder.build(event, "echo", event.getSource(),
        lowerResponse.getPayload() + " " + upperResponse.getPayload());
  }

  /**
   * Processes invert request by reversing the string.
   *
   * @param event      the invert event
   * @param dispatcher the dispatcher (not used by this method)
   * @return reversed string event
   */
  public Event processInvert(Event event, EventBuilder eventBuilder, Dispatcher dispatcher) {
    return eventBuilder.build(event, "invert", event.getSource(),
        new StringBuilder((String) event.getPayload())
            .reverse()
            .toString());
  }
}
