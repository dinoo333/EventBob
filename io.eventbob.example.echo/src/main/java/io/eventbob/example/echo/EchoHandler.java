package io.eventbob.example.echo;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;

/**
 * Handler that supports both "echo" and "invert" capabilities.
 * <p>
 * Demonstrates dependency injection via HandlerLifecycle. The handler depends on
 * {@link EchoService} which is provided via constructor injection. The lifecycle
 * implementation wires the service to the handler.
 * </p>
 * <p>
 * Multi-capability pattern: A single handler can respond to multiple capability names.
 * The handler branches on event.getTarget() to determine which behavior to execute.
 * </p>
 * <ul>
 *   <li>"echo" - Dispatches to "lower" and "upper" capabilities, returns combined result</li>
 *   <li>"invert" - Reverses the input string without external dispatch</li>
 * </ul>
 */
@Capability("echo")
@Capability("invert")
public class EchoHandler implements EventHandler {
  private final EchoService echoService;

  /**
   * Creates an echo handler with the specified service.
   * <p>
   * This constructor demonstrates dependency injection. In the POJO days, handlers
   * had no-arg constructors and no dependencies. With lifecycle support, handlers
   * can receive dependencies (services, repositories, HTTP clients, etc.) that are
   * wired by the lifecycle implementation.
   * </p>
   *
   * @param echoService service that implements echo and invert logic
   */
  public EchoHandler(EchoService echoService) {
    this.echoService = echoService;
  }

  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    return "invert".equals(event.getTarget())
        ? handleInvert(event)
        : handleEcho(event);
  }

  private Event handleInvert(Event event) {
    String input = (String) event.getPayload();
    String reversed = echoService.processInvert(input);
    return Event.builder()
        .source("invert")
        .target(event.getSource())
        .payload(reversed)
        .build();
  }

  private Event handleEcho(Event event) throws EventHandlingException {
    String result = echoService.processEcho(event);
    return Event.builder()
        .source("echo")
        .target(event.getSource())
        .payload(result)
        .build();
  }
}
