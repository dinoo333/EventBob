package io.eventbob.core.eventrouting;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating error events when error handlers fail or return null.
 */
class DefaultErrorEvent {

  static Event create(Throwable error, Event originalEvent) {
    return originalEvent.toBuilder()
        .payload(new LinkedHashMap<>(Map.of(
            "errorMessage", error.getMessage(),
            "errorType", error.getClass().getName(),
            "originalEvent", originalEvent)))
        .build();
  }

  private DefaultErrorEvent() {
    // Utility class
  }
}
