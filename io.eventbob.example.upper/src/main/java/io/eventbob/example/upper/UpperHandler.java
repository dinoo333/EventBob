package io.eventbob.example.upper;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

/**
 * Handler that provides uppercase transformation capability.
 * <p>
 * Demonstrates dependency injection via HandlerLifecycle. The handler depends on
 * {@link UpperService} which is provided via constructor injection. The lifecycle
 * implementation wires the service to the handler.
 * </p>
 */
@Capability("upper")
public class UpperHandler implements EventHandler {
  private final UpperService upperService;

  /**
   * Creates an upper handler with the specified service.
   * <p>
   * This constructor demonstrates dependency injection. Handlers receive dependencies
   * (services, repositories, HTTP clients, etc.) that are wired by the lifecycle
   * implementation.
   * </p>
   *
   * @param upperService service that implements uppercase transformation logic
   */
  public UpperHandler(UpperService upperService) {
    this.upperService = upperService;
  }

  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    String input = (String) event.getPayload();
    String uppercased = upperService.processUppercase(input);

    return event.toBuilder()
        .source("upper")
        .target(event.getSource())
        .payload(uppercased)
        .build();
  }
}
