package io.eventbob.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Transport envelope for in-process communication between handlers within a microlith.
 *
 * <p><b>Not a domain event.</b> Event is a request/response wrapper containing routing
 * information (source, target), parameters, metadata, and an optional payload.
 * It is the message format for EventHandler communication, not a record of a domain occurrence.
 *
 * <p>Events are immutable and passed between EventHandler implementations via EventBob.
 * An event may carry an error to indicate processing failure.
 */
public final class Event {
  private final String source;
  private final String target;
  private final Map<String, Object> parameters;
  private final Map<String, Object> metadata;
  private final Object payload;

  private Event(Builder builder) {
    this.source = reqNonBlank(builder.source, "source");
    this.target = reqNonBlank(builder.target, "target");
    this.parameters = copy(builder.parameters);
    this.metadata = copy(builder.metadata);
    this.payload = builder.payload;
  }

  private static String reqNonBlank(String v, String name) {
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return v;
  }

  private static Map<String, Object> copy(Map<String, Object> in) {
    if (in == null || in.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(in));
  }

  /**
   * Create a new builder.
   *
   * @return  The builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a new builder pre-populated with a deep copy of this event's data.
   */
  public Builder toBuilder() {
    return new Builder()
        .source(this.source)
        .target(this.target)
        .parameters(this.parameters)
        .metadata(this.metadata)
        .payload(this.payload);
  }

  /**
   * Create a new builder pre-populated with a deep copy of this event's data, but with a new
   * source and target.
   *
   * @param source The source
   * @param target The target
   * @return The builder.
   */
  public Builder toBuilder(String source, String target) {
    return new Builder()
        .source(source)
        .target(target)
        .parameters(this.parameters)
        .metadata(this.metadata)
        .payload(this.payload);
  }

  public String getSource() {
    return source;
  }

  public String getTarget() {
    return target;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Object getPayload() {
    return payload;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Event event)) {
      return false;
    }
    return source.equals(event.source)
        && target.equals(event.target)
        && parameters.equals(event.parameters)
        && metadata.equals(event.metadata)
        && Objects.equals(payload, event.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, target, parameters, metadata, payload);
  }

  @Override
  public String toString() {
    return "Event{"
        + "source='" + source + '\''
        + ", target='" + target + '\''
        + ", parameters=" + parameters
        + ", metadata=" + metadata
        + ", payload=" + payload
        + '}';
  }

  /**
   * Builder for {@link Event}.
   */
  public static final class Builder {
    private String source;
    private String target;
    private Map<String, Object> parameters = Collections.emptyMap();
    private Map<String, Object> metadata = Collections.emptyMap();
    private Object payload;

    private static Map<String, Object> mutable(Map<String, Object> m) {
      return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
    }

    /**
     * Set the source.
     *
     * @param source The source
     * @return This builder
     */
    public Builder source(String source) {
      this.source = source;
      return this;
    }

    /**
     * Set the target.
     *
     * @param target The target
     * @return This builder
     */
    public Builder target(String target) {
      this.target = target;
      return this;
    }

    /**
     * Set the parameters.
     *
     * @param parameters The parameters
     * @return This builder
     */
    public Builder parameters(Map<String, Object> parameters) {
      this.parameters = mutable(parameters);
      return this;
    }

    /**
     * Set the metadata.
     *
     * @param metadata The metadata
     * @return  This builder
     */
    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = mutable(metadata);
      return this;
    }

    /**
     * Set the payload.
     *
     * @param payload The payload
     * @return This builder
     */
    public Builder payload(Object payload) {
      this.payload = payload;
      return this;
    }

    /**
     * Build the event.
     *
     * @return The event.
     */
    public Event build() {
      return new Event(this);
    }

    /**
     * Convenience method to build an event with a payload.
     *
     * @param payload The payload
     * @return The event
     */
    public Event build(Object payload) {
      this.payload = payload;
      return build();
    }
  }
}
