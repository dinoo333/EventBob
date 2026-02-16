package io.eventbob.core.eventrouting;

public class HandlerNotFoundException extends RuntimeException {
  public HandlerNotFoundException(String target) {
    super("No handler registered for target: " + target);
  }

  public HandlerNotFoundException(String target, Event event) {
    super("No handler registered for target: " + target + "; event = " + event.toString());
  }
}
