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
 * <p>Requests and responses have independent policy sets, registered via
 * {@link Builder#requestPolicy} and {@link Builder#responsePolicy} respectively. When
 * {@link #request} or {@link #response} is called for a route with registered
 * callbacks, they run in registration order against the in-progress {@link Event.Builder}; an
 * unmatched route builds unmodified.
 */
public class EventBuilder {
  private final Map<RouteKey, List<Consumer<Event.Builder>>> requestPolicies;
  private final Map<RouteKey, List<Consumer<Event.Builder>>> responsePolicies;

  private EventBuilder(Builder builder) {
    this.requestPolicies = copyOf(builder.requestPolicies);
    this.responsePolicies = copyOf(builder.responsePolicies);
  }

  private static Map<RouteKey, List<Consumer<Event.Builder>>> copyOf(
      Map<RouteKey, List<Consumer<Event.Builder>>> source) {
    return source.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /**
   * Build a request event derived from the given template, targeting the given route.
   *
   * <p>The source defaults to {@code template.getTarget()}; {@code target} must be supplied
   * explicitly since no template default exists for it. Request policies registered for the
   * resolved source/target route run <b>eagerly, exactly once</b>, against the returned
   * {@link Event.Builder}, before this method returns. Calling {@code .source(...)} or
   * {@code .target(...)} on the returned builder afterward changes only the final
   * {@link Event}'s fields — it does <b>not</b> re-run policy resolution.
   *
   * @param template The event to copy as a starting point.
   * @param target   The outbound event's target.
   * @return The builder, with request policies for this route already applied.
   */
  public Event.Builder request(Event template, String target) {
    String source = template.getTarget();
    var builder = template
        .toBuilder()
        .source(source)
        .target(target);
    applyPolicies(requestPolicies, source, target, builder);
    return builder;
  }

  /**
   * Build a response event derived from the given template, swapping source and target.
   *
   * <p>{@code source} defaults to {@code template.getTarget()} and {@code target} defaults to
   * {@code template.getSource()} (a full swap). Response policies registered for the resolved
   * source/target route run <b>eagerly, exactly once</b>, against the returned
   * {@link Event.Builder}, before this method returns. Calling {@code .source(...)} or
   * {@code .target(...)} on the returned builder afterward changes only the final
   * {@link Event}'s fields — it does <b>not</b> re-run policy resolution.
   *
   * @param template The event to copy as a starting point.
   * @return The builder, with response policies for this route already applied.
   */
  public Event.Builder response(Event template) {
    String source = template.getTarget();
    String target = template.getSource();
    var builder = template
        .toBuilder()
        .source(source)
        .target(target);
    applyPolicies(responsePolicies, source, target, builder);
    return builder;
  }

  private static void applyPolicies(
      Map<RouteKey, List<Consumer<Event.Builder>>> policies,
      String source,
      String target,
      Event.Builder builder) {
    var matching = policies.get(new RouteKey(source, target));
    if (matching != null) {
      matching.forEach(policy -> policy.accept(builder));
    }
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
    private final Map<RouteKey, List<Consumer<Event.Builder>>> requestPolicies = new HashMap<>();
    private final Map<RouteKey, List<Consumer<Event.Builder>>> responsePolicies = new HashMap<>();

    /**
     * Register a request-building callback for the given source/target route.
     *
     * @param source The route's source (must not be blank).
     * @param target The route's target (must not be blank).
     * @param policy The callback to run when this route is built (must not be null).
     * @return This builder.
     */
    public Builder requestPolicy(String source, String target, Consumer<Event.Builder> policy) {
      register(requestPolicies, source, target, policy);
      return this;
    }

    /**
     * Register a response-building callback for the given source/target route.
     *
     * @param source The route's source (must not be blank).
     * @param target The route's target (must not be blank).
     * @param policy The callback to run when this route is built (must not be null).
     * @return This builder.
     */
    public Builder responsePolicy(String source, String target, Consumer<Event.Builder> policy) {
      register(responsePolicies, source, target, policy);
      return this;
    }

    private static void register(
        Map<RouteKey, List<Consumer<Event.Builder>>> policies,
        String source,
        String target,
        Consumer<Event.Builder> policy) {
      reqNonBlank(source, "source");
      reqNonBlank(target, "target");
      Objects.requireNonNull(policy, "policy");
      policies.computeIfAbsent(new RouteKey(source, target), key -> new ArrayList<>())
          .add(policy);
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
