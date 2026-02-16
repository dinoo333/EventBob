package io.eventbob.example.lower;

import io.eventbob.core.eventrouting.Capability;
import io.eventbob.core.eventrouting.Dispatcher;
import io.eventbob.core.eventrouting.Event;
import io.eventbob.core.eventrouting.EventHandler;
import io.eventbob.core.eventrouting.EventHandlingException;

@Capability("lower")
public class LowerHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    String input = (String) event.getPayload();
    String lowercased = input.toLowerCase();

    return event.toBuilder()
        .payload(lowercased)
        .build();
  }
}
