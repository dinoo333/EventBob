package io.eventbob.core.eventrouting;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain event data structure.
 */
public final class Event implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final String source;
  private final String target;
  private final Map<String, Serializable> parameters;
  private final Map<String, Serializable> metadata;
  private final Serializable payload;

  private Event(Builder builder) {
    this.source = reqNonBlank(builder.source, "source");
    this.target = reqNonBlank(builder.target, "target");
    this.parameters = copy(builder.parameters);
    this.metadata = copy(builder.metadata);
    this.payload = builder.payload;
  }

  /**
   * Create a new builder pre-populated with a deep copy of this event's data.
   */
  public Builder toBuilder() {
    Builder b = new Builder();
    b.source = this.source;
    b.target = this.target;
    if (!this.parameters.isEmpty()) {
      b.parameters = new LinkedHashMap<>(this.parameters);
    }
    if (!this.metadata.isEmpty()) {
      b.metadata = new LinkedHashMap<>(this.metadata);
    }
    b.payload = this.payload;
    return b;
  }

  private static String reqNonBlank(String v, String name) {
    if (v == null || v.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return v;
  }

  private static Map<String, Serializable> copy(Map<String, Serializable> in) {
    if (in == null || in.isEmpty()) return Collections.emptyMap();
    return Collections.unmodifiableMap(new LinkedHashMap<>(in));
  }

  public String getSource() {
    return source;
  }

  public String getTarget() {
    return target;
  }

  public Map<String, Serializable> getParameters() {
    return parameters;
  }

  public Map<String, Serializable> getMetadata() {
    return metadata;
  }

  public Serializable getPayload() {
    return payload;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Event event)) return false;
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
    return "Event{" +
        "source='" + source + '\'' +
        "target='" + target + '\'' +
        ", parameters=" + parameters +
        ", metadata=" + metadata +
        ", payload=" + payload +
        '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String source;
    private String target;
    private Map<String, Serializable> parameters = Collections.emptyMap();
    private Map<String, Serializable> metadata = Collections.emptyMap();
    private Serializable payload;

    private static Map<String, Serializable> mutable(Map<String, Serializable> m) {
      if (m == null || m.isEmpty()) return Collections.emptyMap();
      return new LinkedHashMap<>(m);
    }

    public Builder source(String source) {
      this.source = source;
      return this;
    }

    public Builder target(String target) {
      this.target = target;
      return this;
    }

    public Builder parameters(Map<String, Serializable> parameters) {
      this.parameters = mutable(parameters);
      return this;
    }

    public Builder metadata(Map<String, Serializable> metadata) {
      this.metadata = mutable(metadata);
      return this;
    }

    public Builder payload(Serializable payload) {
      this.payload = payload;
      return this;
    }

    public Event build() {
      return new Event(this);
    }
  }
}
