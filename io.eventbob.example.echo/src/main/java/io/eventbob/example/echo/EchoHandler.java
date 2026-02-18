package io.eventbob.example.echo;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;
import java.util.concurrent.TimeUnit;

@Capability("echo")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {

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
    try {
      return dispatcher.send(upperRequest, (err, evt) -> null)
          .get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new EventHandlingException("Failed to call upper", e);
    }
  }

  private Event getLower(Event event, Dispatcher dispatcher) {
    Event lowerRequest = Event.builder()
        .source("echo")
        .target("lower")
        .payload(event.getPayload())
        .build();
    try {
      return dispatcher.send(lowerRequest, (err, evt) -> null)
          .get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new EventHandlingException("Failed to call lower", e);
    }
  }
}
