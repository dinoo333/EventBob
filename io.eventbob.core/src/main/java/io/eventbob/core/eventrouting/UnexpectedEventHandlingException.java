package io.eventbob.core.eventrouting;

public class UnexpectedEventHandlingException extends EventHandlingException {
  public UnexpectedEventHandlingException(Throwable cause) {
    super("Unexpected error handling event", cause);
  }

  public UnexpectedEventHandlingException(String message, Throwable cause) {
    super(message, cause);
  }
}
