package io.eventbob.core.eventrouting;

public interface EventHandler {
  Event handle(Event event) throws EventHandlingException;
}
