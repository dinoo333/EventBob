package io.eventbob.core.eventrouting;

import java.io.Serializable;

/**
 * Exception thrown when handling an Event fails.
 * Carries an optional serializable payload for diagnostic or recovery use.
 */
public class EventHandlingException extends RuntimeException {
  private final Serializable payload;

  // Base constructors (matching java.lang.Exception)
  public EventHandlingException(String message) {
    super(message);
    this.payload = null;
  }

  public EventHandlingException(String message, Throwable cause) {
    super(message, cause);
    this.payload = null;
  }

  public EventHandlingException(String message, Serializable payload) {
    super(message);
    this.payload = payload;
  }

  /**
   * Return the associated payload (may be null).
   */
  public Serializable getPayload() {
    return payload;
  }
}
