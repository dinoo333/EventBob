package io.eventbob.example.lower;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

/**
 * Handler that provides lowercase transformation capability.
 * <p>
 * Demonstrates dependency injection via HandlerLifecycle. The handler depends on
 * {@link LowerService} which is provided via constructor injection. The lifecycle
 * implementation wires the service to the handler.
 * </p>
 */
@Capability("lower")
public class LowerHandler implements EventHandler {
  private final LowerService lowerService;

  /**
   * Creates a lower handler with the specified service.
   * <p>
   * This constructor demonstrates dependency injection. Handlers receive dependencies
   * (services, repositories, HTTP clients, etc.) that are wired by the lifecycle
   * implementation.
   * </p>
   *
   * @param lowerService service that implements lowercase transformation logic
   */
  public LowerHandler(LowerService lowerService) {
    this.lowerService = lowerService;
  }

  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    String input = (String) event.getPayload();
    String lowercased = lowerService.processLowercase(input);

    return event.toBuilder()
        .source("lower")
        .target(event.getSource())
        .payload(lowercased)
        .build();
  }
}
