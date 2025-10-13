package io.eventbob.core;

import java.io.Serial;

public class HandlerNotFoundException extends EventHandlingException {
  private final String target;

  public HandlerNotFoundException(String target, Event event) {
    super("No handler found for target: " + target, event);
    this.target = target;
  }

  public String getTarget() {
    return target;
  }
}
