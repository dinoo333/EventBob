package io.eventbob.example.lower;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

@Capability("lower")
public class LowerHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    String input = (String) event.getPayload();
    String lowercased = input.toLowerCase();

    return event.toBuilder()
        .source("lower")
        .target(event.getSource())
        .payload(lowercased)
        .build();
  }
}
