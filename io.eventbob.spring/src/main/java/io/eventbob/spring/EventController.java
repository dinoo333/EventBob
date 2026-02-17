package io.eventbob.spring;

import io.eventbob.core.Event;
import io.eventbob.core.EventBob;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
   * @param dto The event DTO to map.
   * @return The domain Event built from the DTO.
   */
  private Event toEvent(EventDto dto) {
    return Event.builder()
        .source(dto.getSource())
        .target(dto.getTarget())
        .parameters(dto.getParameters())
        .metadata(dto.getMetadata())
        .payload(dto.getPayload())
        .build();
  }

  /**
   * Map domain Event to EventDto.
   *
   * @param event The domain Event to map.
   * @return The event DTO.
   */
  private EventDto fromEvent(Event event) {
    return new EventDto(
        event.getSource(),
        event.getTarget(),
        event.getParameters(),
        event.getMetadata(),
        event.getPayload()
    );
  }
}
