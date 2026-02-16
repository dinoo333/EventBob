package io.eventbob.core.eventrouting;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Dispatcher is responsible for sending events while within event handlers.
 * It abstracts away the underlying transport mechanism (e.g., HTTP, gRPC, message queues)
 * and provides a simple API for event emission.
 */
public interface Dispatcher {
  /**
   * Sends an event and waits for a response.
   *
   * <p>This is used for request-response interactions where the sender expects a reply.
   *
   * @param event The event to send.
   * @return The result of processing the event, wrapped as a completable future.
   */
  CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError);
}
