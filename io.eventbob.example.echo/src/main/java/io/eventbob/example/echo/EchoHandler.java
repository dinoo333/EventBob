package io.eventbob.example.echo;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;
import java.util.concurrent.TimeUnit;

/**
 * Handler that supports both "echo" and "invert" capabilities.
 *
 * <p>Multi-capability pattern: A single handler can respond to multiple capability names.
 * The handler branches on event.getTarget() to determine which behavior to execute.
 *
 * <ul>
 *   <li>"echo" - Dispatches to "lower" and "upper" capabilities, returns combined result</li>
 *   <li>"invert" - Reverses the input string without external dispatch</li>
 * </ul>
 */
@Capability("echo")
@Capability("invert")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    return "invert".equals(event.getTarget())
        ? handleInvert(event)
        : handleEcho(event, dispatcher);
  }

  private Event handleInvert(Event event) {
    String input = (String) event.getPayload();
    String reversed = new StringBuilder(input).reverse().toString();
    return Event.builder()
        .source("invert")
        .target(event.getSource())
        .payload(reversed)
        .build();
  }

  private Event handleEcho(Event event, Dispatcher dispatcher) throws EventHandlingException {
    var lower = getLower(event, dispatcher);
    var upper = getUpper(event, dispatcher);
    return Event.builder()
        .source("echo")
        .target(event.getSource())
        .payload(lower.getPayload() + " " + upper.getPayload())
        .build();
  }

  private Event getUpper(Event event, Dispatcher dispatcher) {
    Event upperRequest = Event.builder()
        .source("echo")
        .target("upper")
        .payload(event.getPayload())
        .build();
    return dispatcher.send(upperRequest, (err, evt) -> null, 1000);
  }

  private Event getLower(Event event, Dispatcher dispatcher) {
    Event lowerRequest = Event.builder()
        .source("echo")
        .target("lower")
        .payload(event.getPayload())
        .build();
    return dispatcher.send(lowerRequest, (err, evt) -> null, 1000);
  }
}
