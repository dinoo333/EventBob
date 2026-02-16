package io.eventbob.spring;

import io.eventbob.core.eventrouting.Event;
import io.eventbob.core.eventrouting.EventBob;
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
   * <p>Accepts an Event as JSON in the request body, routes it to the appropriate
   * handler based on the event's target field, and returns the result Event.
   * The method returns CompletableFuture which Spring MVC suspends asynchronously.
   *
   * @param event The input event to process.
   * @return CompletableFuture containing the result event from the handler.
   */
  @PostMapping("/events")
  public CompletableFuture<Event> processEvent(@RequestBody Event event) {
    return eventBob.processEvent(event, (error, originalEvent) -> null);
  }
}
