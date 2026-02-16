package io.eventbob.example.echo;

import io.eventbob.core.eventrouting.Capability;
import io.eventbob.core.eventrouting.Dispatcher;
import io.eventbob.core.eventrouting.Event;
import io.eventbob.core.eventrouting.EventHandler;
import io.eventbob.core.eventrouting.EventHandlingException;
import java.util.concurrent.TimeUnit;

@Capability("echo")
public class EchoHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    Event lowerRequest = Event.builder()
        .source("echo")
        .target("lower")
        .payload(event.getPayload())
        .build();

    try {
      Event lowerResult = dispatcher.send(lowerRequest, (err, evt) -> null)
          .get(1, TimeUnit.SECONDS);

      return event.toBuilder()
          .payload(lowerResult.getPayload())
          .build();
    } catch (Exception e) {
      throw new EventHandlingException("Failed to call lower", e);
    }
  }
}
