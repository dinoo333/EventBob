package io.eventbob.core;

public interface EventHandler {
  Event handle(Event event) throws EventHandlingException;
}
