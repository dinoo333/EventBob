package io.eventbob.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

  /**
   * Sends an event and waits for a response, with a timeout.
   *
   * <p>This is used for request-response interactions where the sender expects a reply, but wants to
   * avoid waiting indefinitely.
   *
   * @param event The event to send.
   * @param timeoutMillis The maximum time to wait for a response, in milliseconds.
   * @return The result of processing the event.
   * @throws EventHandlingException If an error occurs during event handling or if the timeout is reached.
   */
  default Event send(Event event, BiFunction<Throwable, Event, Event> onError, long timeoutMillis) {
    try {
      return send(event, onError).get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventHandlingException("Interrupted while sending event", e);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new EventHandlingException("Failed to send event", e.getCause());
    } catch (java.util.concurrent.TimeoutException e) {
      throw new EventHandlingException("Timeout waiting for response", e);
    }
  }
}
