package io.eventbob.core.eventrouting;

import java.io.Serializable;

public class HandlerNotFoundException extends EventHandlingException {
  private final String target;

  public HandlerNotFoundException(String target) {
    super("No handler registered for target: " + target);
    this.target = target;
  }

  public HandlerNotFoundException(String target, Event event) {
    super("No handler registered for target: " + target, (Serializable) event);
    this.target = target;
  }

  public String getTarget() {
    return target;
  }
}
