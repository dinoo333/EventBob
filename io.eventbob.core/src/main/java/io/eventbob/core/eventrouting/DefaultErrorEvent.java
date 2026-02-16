package io.eventbob.core.eventrouting;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for creating error events when error handlers fail or return null.
 */
class DefaultErrorEvent {

  static Event create(Throwable error, Event originalEvent) {
    Map<String, Serializable> errorPayload = new LinkedHashMap<>();
    errorPayload.put("errorMessage", error.getMessage() != null ? error.getMessage() : "null");
    errorPayload.put("errorType", error.getClass().getName());
    errorPayload.put("originalEvent", originalEvent);

    return originalEvent.toBuilder()
        .payload((Serializable) errorPayload)
        .build();
  }

  private DefaultErrorEvent() {
    // Utility class
  }
}
