package io.eventbob.core;

/**
 * Exception thrown when no handler is registered for a given target.
 */
public class HandlerNotFoundException extends RuntimeException {
  /**
   * Constructor.
   *
   * @param target The target of the event.
   */
  public HandlerNotFoundException(String target) {
    super("No handler registered for target: " + target);
  }

  /**
   * Constructor.
   *
   * @param target The target of the event.
   * @param event The event.
   */
  public HandlerNotFoundException(String target, Event event) {
    super("No handler registered for target: " + target + "; event = " + event.toString());
  }
}
