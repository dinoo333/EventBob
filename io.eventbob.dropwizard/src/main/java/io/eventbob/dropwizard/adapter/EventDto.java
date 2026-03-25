package io.eventbob.dropwizard.adapter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.eventbob.core.Event;

import java.util.Collections;
import java.util.Map;

/**
 * Data Transfer Object for Event serialization across HTTP boundaries.
 * <p>
 * This DTO exists in the infrastructure layer and carries Jackson annotations
 * to keep the core Event class framework-agnostic. It translates between the
 * domain Event and JSON representations used in HTTP communication.
 * </p>
 */
public record EventDto(
    String source,
    String target,
    Map<String, Object> parameters,
    Map<String, Object> metadata,
    Object payload
) {

  @JsonCreator
  public EventDto(
      @JsonProperty("source") String source,
      @JsonProperty("target") String target,
      @JsonProperty("parameters") Map<String, Object> parameters,
      @JsonProperty("metadata") Map<String, Object> metadata,
      @JsonProperty("payload") Object payload) {
    this.source = source;
    this.target = target;
    this.parameters = parameters != null ? parameters : Collections.emptyMap();
    this.metadata = metadata != null ? metadata : Collections.emptyMap();
    this.payload = payload;
  }

  /**
   * Converts a domain Event to an EventDto for serialization.
   *
   * @param event the domain event
   * @return the DTO representation
   */
  public static EventDto fromEvent(Event event) {
    return new EventDto(
        event.getSource(),
        event.getTarget(),
        event.getParameters(),
        event.getMetadata(),
        event.getPayload()
    );
  }

  /**
   * Converts this DTO back to a domain Event.
   *
   * @return the domain event
   */
  public Event toEvent() {
    return Event.builder()
        .source(source)
        .target(target)
        .parameters(parameters)
        .metadata(metadata)
        .payload(payload)
        .build();
  }
}
