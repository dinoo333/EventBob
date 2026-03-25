package io.eventbob.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Delegating event handler that routes events to a target-specific {@link EventHandler}.
 */
public class EventBob implements AutoCloseable {
  private final ExecutorService backgroundExecutor;
  private final Dispatcher dispatcher;
  private final Map<String, EventHandler> handlers;

  /**
   * Direct constructor (map is defensively copied and made unmodifiable).
   */
  private EventBob(Builder builder) {
    this.handlers = Map.copyOf(Objects.requireNonNull(builder.handlers, "handlers"));
    this.backgroundExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.dispatcher = this::processEvent;
  }

  /**
   * Fluent builder entry point.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Process an incoming event by routing it to the appropriate handler based on the event's target.
   */
  public CompletableFuture<Event> processEvent(Event event, BiFunction<Throwable, Event, Event> onError) {
    return CompletableFuture.supplyAsync(() -> {
          EventHandler delegate = findHandler(event);
          return delegate.handle(event, dispatcher);
        }, backgroundExecutor)
        .exceptionally(e -> {
          var errorEvent = onError.apply(e, event);
          if (errorEvent == null) {
            return DefaultErrorEvent.create(e, event);
          }
          return errorEvent;
        });
  }

  private EventHandler findHandler(Event event) {
    String target = event.getTarget();
    EventHandler delegate = handlers.get(target);
    if (delegate == null) {
      throw new HandlerNotFoundException(target, event);
    }
    return delegate;
  }

  /**
   * Shuts down EventBob and waits for in-flight events to complete.
   * <p>
   * Blocks until all currently executing handlers finish or a 30-second
   * timeout elapses. This ensures that handler lifecycles can be safely
   * torn down after this method returns.
   * </p>
   */
  @Override
  public void close() {
    backgroundExecutor.shutdown();
    try {
      backgroundExecutor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Builder for {@link EventBob}.
   */
  public static final class Builder {
    private final Map<String, EventHandler> handlers = new LinkedHashMap<>();

    /**
     * Register a handler for the given target string.
     *
     * @param target The target identifier (must be non-blank).
     * @param handler The handler to register (must be non-null).
     * @return This builder for fluent chaining.
     */
    public Builder handler(String target, EventHandler handler) {
      if (target == null || target.isBlank()) {
        throw new IllegalArgumentException("target must not be blank");
      }
      Objects.requireNonNull(handler, "handler");
      this.handlers.put(target, handler);
      return this;
    }

    /**
     * Build the EventRouter with an unmodifiable handler map.
     */
    public EventBob build() {
      return new EventBob(this);
    }
  }
}
