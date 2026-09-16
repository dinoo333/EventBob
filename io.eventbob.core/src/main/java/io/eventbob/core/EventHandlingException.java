package io.eventbob.core;

/**
 * Exception thrown when handling an Event fails.
 */
public class EventHandlingException extends RuntimeException {
  /**
   * Constructor.
   *
   * @param message The exception message.
   */
  public EventHandlingException(String message) {
    super(message);
  }

  /**
   * Constructor.
   *
   * @param message The exception message.
   * @param cause The cause of the exception.
   */
  public EventHandlingException(String message, Throwable cause) {
    super(message, cause);
  }
}
