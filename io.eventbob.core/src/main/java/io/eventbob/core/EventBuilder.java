package io.eventbob.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A route-keyed extension point invoked during event construction.
 *
 * <p>Callbacks are registered per source/target route via {@link Builder#policy}. When
 * {@link #build} is called for a route with registered callbacks, they run in registration
 * order against the in-progress {@link Event.Builder}; an unmatched route builds unmodified.
 */
public class EventBuilder {
  private final Map<RouteKey, List<Consumer<Event.Builder>>> policies;

  private EventBuilder(Builder builder) {
    this.policies = builder.policies.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /**
   * Build an event for the given route, running any callbacks registered for that route.
   *
   * @param template The event to copy as a starting point.
   * @param source   The outbound event's source.
   * @param target   The outbound event's target.
   * @param payload  The outbound event's payload.
   * @return The built event.
   */
  public Event build(Event template, String source, String target, Object payload) {
    var builder = template
        .toBuilder()
        .source(source)
        .target(target)
        .payload(payload);
    var policies = this.policies.get(new RouteKey(source, target));
    if (policies != null) {
      policies.forEach(policy -> policy.accept(builder));
    }
    return builder.build();
  }

  /**
   * Create a new builder.
   *
   * @return The builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  private record RouteKey(String source, String target) {
  }

  /**
   * Builder for {@link EventBuilder}.
   */
  public static class Builder {
    private final Map<RouteKey, List<Consumer<Event.Builder>>> policies = new HashMap<>();

    /**
     * Register a callback for the given source/target route.
     *
     * @param source The route's source (must not be blank).
     * @param target The route's target (must not be blank).
     * @param policy The callback to run when this route is built (must not be null).
     * @return This builder.
     */
    public Builder policy(String source, String target, Consumer<Event.Builder> policy) {
      reqNonBlank(source, "source");
      reqNonBlank(target, "target");
      Objects.requireNonNull(policy, "policy");
      policies.computeIfAbsent(new RouteKey(source, target), key -> new ArrayList<>())
          .add(policy);
      return this;
    }

    private static void reqNonBlank(String v, String name) {
      if (v == null || v.isBlank()) {
        throw new IllegalArgumentException(name + " must not be blank");
      }
    }

    /**
     * Build the event builder.
     *
     * @return The event builder.
     */
    public EventBuilder build() {
      return new EventBuilder(this);
    }
  }
}
