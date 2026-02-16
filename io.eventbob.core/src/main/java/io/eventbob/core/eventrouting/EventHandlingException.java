package io.eventbob.core.eventrouting;

/**
 * Exception thrown when handling an Event fails.
 */
public class EventHandlingException extends RuntimeException {
  // Base constructors (matching java.lang.Exception)
  public EventHandlingException(String message) {
    super(message);
  }

  public EventHandlingException(String message, Throwable cause) {
    super(message, cause);
  }
}
