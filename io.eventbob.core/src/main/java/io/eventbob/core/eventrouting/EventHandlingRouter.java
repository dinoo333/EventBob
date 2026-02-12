package io.eventbob.core.eventrouting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Delegating event handler that routes events to a target-specific {@link EventHandler}.
 */
public class EventHandlingRouter implements EventHandler {

  private final Map<String, EventHandler> handlers;

  /**
   * Direct constructor (map is defensively copied and made unmodifiable).
   */
  private EventHandlingRouter(Builder builder) {
    this.handlers = Map.copyOf(Objects.requireNonNull(builder.handlers, "handlers"));
  }

  /**
   * Fluent builder entry point.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Given an event, when handling then the event is routed by target to a specific handler.
   * Given an event, when no handler is registered for the target, then a {@link HandlerNotFoundException} is thrown.
   * Given a null event, when handling, then a {@link NullPointerException} is thrown.
   */
  @Override
  public Event handle(Event event) throws EventHandlingException {
    Objects.requireNonNull(event, "event");
    String target = event.getTarget();
    EventHandler delegate = handlers.get(target);
    if (delegate == null) {
      throw new HandlerNotFoundException(target, event);
    }
    return delegate.handle(event);
  }

  /**
   * Builder for {@link EventHandlingRouter}.
   */
  public static final class Builder {
    private final Map<String, EventHandler> handlers = new LinkedHashMap<>();

    /**
     * Add a single handler mapping.
     */
    public Builder handler(String target, EventHandler handler) {
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(handler, "handler");
      handlers.put(target, handler);
      return this;
    }

    /**
     * Build the EventRouter with an unmodifiable handler map.
     */
    public EventHandlingRouter build() {
      return new EventHandlingRouter(this);
    }
  }
}
