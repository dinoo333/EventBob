package io.eventbob.example.upper;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

@Capability("upper")
public class UpperHandler implements EventHandler {
  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    String input = (String) event.getPayload();
    String uppercased = input.toUpperCase();

    return event.toBuilder()
        .source("upper")
        .target(event.getSource())
        .payload(uppercased)
        .build();
  }
}
