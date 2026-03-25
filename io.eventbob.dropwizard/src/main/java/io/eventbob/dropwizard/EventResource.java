package io.eventbob.dropwizard;

import io.eventbob.core.Event;
import io.eventbob.core.EventBob;
import io.eventbob.dropwizard.adapter.EventDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.concurrent.CompletableFuture;

/**
 * JAX-RS resource for event processing.
 *
 * <p>Exposes a single POST /events endpoint that accepts an Event as JSON,
 * processes it through EventBob, and returns the result Event as JSON.
 * Jersey handles the async suspension of the CompletableFuture return type,
 * making the endpoint non-blocking.
 *
 * <p>Uses EventDto as the request/response DTO to keep Jackson deserialization
 * concerns out of the domain Event class.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    private final EventBob eventBob;

    public EventResource(EventBob eventBob) {
        this.eventBob = eventBob;
    }

    /**
     * Process an event through EventBob.
     *
     * <p>Accepts an EventDto as JSON in the request body, maps it to a domain Event,
     * routes it to the appropriate handler based on the event's target field,
     * and returns the result Event as EventDto.
     * The method returns CompletableFuture which Jersey suspends asynchronously.
     *
     * @param eventDto The input event DTO to process.
     * @return CompletableFuture containing the result event DTO from the handler.
     */
    @POST
    public CompletableFuture<EventDto> processEvent(EventDto eventDto) {
        Event event = eventDto.toEvent();
        return eventBob.processEvent(event, (error, originalEvent) -> null)
            .thenApply(EventDto::fromEvent);
    }
}
