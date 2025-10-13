package io.eventbob.core;

public class UnexpectedEventHandlingException extends EventHandlingException {
  public UnexpectedEventHandlingException(Throwable e) {
    super("Unexpected error during event handling", e);
  }
}
