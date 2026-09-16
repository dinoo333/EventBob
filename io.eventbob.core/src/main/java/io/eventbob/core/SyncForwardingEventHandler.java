package io.eventbob.core;

import java.util.function.BiFunction;

/**
 * Base class for synchronous event handlers that forward requests and responses.
 *
 * @param <I> The forwarded request type
 * @param <O> The response type from the forwarded-to system.
 */
public abstract class SyncForwardingEventHandler<I, O> implements EventHandler {
  private final BiFunction<SyncForwardingEventHandler<I, O>, Event, I> requestBuilder;
  private final BiFunction<SyncForwardingEventHandler<I, O>, O, Event> responseParser;
  private final BiFunction<SyncForwardingEventHandler<I, O>, I, O> delegate;

  /**
   * Creates a new synchronous forwarding event handler.
   *
   * @param requestBuilder Function that builds the request from the event.
   * @param responseParser Function that parses the response from the delegate.
   * @param delegate       Function that forwards the request to the delegate.
   */
  public SyncForwardingEventHandler(
      BiFunction<SyncForwardingEventHandler<I, O>, Event, I> requestBuilder,
      BiFunction<SyncForwardingEventHandler<I, O>, O, Event> responseParser,
      BiFunction<SyncForwardingEventHandler<I, O>, I, O> delegate) {
    this.requestBuilder = requestBuilder;
    this.responseParser = responseParser;
    this.delegate = delegate;
  }

  @Override
  public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
    try {
      I request = requestBuilder.apply(this, event);
      O response = delegate.apply(this, request);
      return responseParser.apply(this, response);
    } catch (EventHandlingException e) {
      throw e;
    } catch (Exception e) {
      throw new EventHandlingException("Unexpected sync handling error: ", e);
    }
  }
}
