package io.eventbob.spring;

import io.eventbob.core.eventrouting.Event;
import io.eventbob.core.eventrouting.EventBob;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for event processing.
 *
 * <p>Exposes a single POST /events endpoint that accepts an Event as JSON,
 * processes it through EventBob, and returns the result Event as JSON.
 * Spring MVC handles the async suspension of the CompletableFuture return type,
 * making the endpoint non-blocking.
 *
 * <p>Uses EventDto as the request/response DTO to keep Jackson deserialization
 * concerns out of the domain Event class.
 */
@RestController
public class EventController {

  private final EventBob eventBob;

  public EventController(EventBob eventBob) {
    this.eventBob = eventBob;
  }

  /**
   * Process an event through EventBob.
   *
   * <p>Accepts an EventDto as JSON in the request body, maps it to a domain Event,
   * routes it to the appropriate handler based on the event's target field,
   * and returns the result Event as EventDto.
   * The method returns CompletableFuture which Spring MVC suspends asynchronously.
   *
   * @param eventDto The input event DTO to process.
   * @return CompletableFuture containing the result event DTO from the handler.
   */
  @PostMapping("/events")
  public CompletableFuture<EventDto> processEvent(@RequestBody EventDto eventDto) {
    Event event = toEvent(eventDto);
    return eventBob.processEvent(event, (error, originalEvent) -> null)
        .thenApply(this::fromEvent);
  }

  /**
   * Map EventDto to domain Event.
   *
   * <p>Converts DTO fields (Object types) to Event fields (Serializable types).
   * Handles null-safe conversions for maps.
   *
   * @param dto The event DTO to map.
   * @return The domain Event built from the DTO.
   */
  private Event toEvent(EventDto dto) {
    return Event.builder()
        .source(dto.getSource())
        .target(dto.getTarget())
        .parameters(toSerializableMap(dto.getParameters()))
        .metadata(toSerializableMap(dto.getMetadata()))
        .payload(toSerializable(dto.getPayload()))
        .build();
  }

  /**
   * Map domain Event to EventDto.
   *
   * <p>Converts Event fields (Serializable types) to DTO fields (Object types)
   * for JSON serialization.
   *
   * @param event The domain Event to map.
   * @return The event DTO.
   */
  private EventDto fromEvent(Event event) {
    return new EventDto(
        event.getSource(),
        event.getTarget(),
        toObjectMap(event.getParameters()),
        toObjectMap(event.getMetadata()),
        event.getPayload()
    );
  }

  /**
   * Convert Map<String, Object> to Map<String, Serializable>.
   * Null-safe: returns empty map if input is null.
   */
  private Map<String, Serializable> toSerializableMap(Map<String, Object> in) {
    if (in == null || in.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Serializable> out = new HashMap<>();
    for (Map.Entry<String, Object> entry : in.entrySet()) {
      out.put(entry.getKey(), toSerializable(entry.getValue()));
    }
    return out;
  }

  /**
   * Convert Map<String, Serializable> to Map<String, Object>.
   * Null-safe: returns empty map if input is null.
   */
  private Map<String, Object> toObjectMap(Map<String, Serializable> in) {
    if (in == null || in.isEmpty()) {
      return Collections.emptyMap();
    }
    return new HashMap<>(in);
  }

  /**
   * Cast Object to Serializable if non-null.
   * If the object is already Serializable, it is cast directly.
   * If not, this will fail at runtime (expected behavior for API contract violations).
   */
  private Serializable toSerializable(Object value) {
    if (value == null) {
      return null;
    }
    return (Serializable) value;
  }
}
