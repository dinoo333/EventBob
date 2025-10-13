package io.eventbob.core;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Decorator for an {@link EventHandler} allowing optional before, after-success,
 * error and timing hooks without changing the wrapped handler.
 */
public final class DecoratedEventHandler implements EventHandler {

  private final EventHandler delegate;
  private final Consumer<Event> before;
  private final BiConsumer<Event, Event> afterSuccess;
  private final BiFunction<Event, Throwable, Event> onError;

  private DecoratedEventHandler(Builder b) {
    this.delegate = b.delegate;
    this.before = b.before;
    this.afterSuccess = b.afterSuccess;
    this.onError = b.onError;
  }

  public static Builder builder() {
    return new Builder();
  }


  /**
   * Given an event, when handling then the before hook is invoked (if any) before delegating to the wrapped handler.
   * Given an event, when handling completes successfully then the after-success hook is invoked (if any).
   * Given an event, when handling fails and an on-error hook is present then the on-error hook is invoked (if any).
   * Given an event, when handling fails with a EventHandlingException and an on-error hook is not present then the EventHandlingException is rethrown.
   * Given an event, when handling fails with a non EventHandlingException and an on-error hook is not present then UnexpectedEventHandlingException is thrown with the original exception as cause.
   * Given a null event, when handling, then a {@link NullPointerException} is thrown.
   */
  @Override
  public Event handle(Event event) throws EventHandlingException {
    Objects.requireNonNull(event, "event");
    if (before != null) {
      before.accept(event);
    }

    try {
      var egressEvent = delegate.handle(event);
      if (afterSuccess != null) {
        afterSuccess.accept(event, egressEvent);
      }
      return egressEvent;
    } catch (EventHandlingException e) {
      if (onError != null) {
        return onError.apply(event, e);
      }
      throw e;
    } catch (Throwable e) {
      if (onError != null) {
        return onError.apply(event, e);
      }
      throw new UnexpectedEventHandlingException(e);
    }
  }

  /**
   * Builder for {@link DecoratedEventHandler}.
   */
  public static final class Builder {
    private EventHandler delegate;
    private Consumer<Event> before;
    private BiConsumer<Event, Event> afterSuccess;
    private BiFunction<Event, Throwable, Event> onError;

    public Builder delegate(EventHandler delegate) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      return this;
    }

    public Builder before(Consumer<Event> before) {
      this.before = before;
      return this;
    }

    public Builder afterSuccess(BiConsumer<Event, Event> afterSuccess) {
      this.afterSuccess = afterSuccess;
      return this;
    }

    public Builder onError(BiFunction<Event, Throwable, Event> onError) {
      this.onError = onError;
      return this;
    }

    public DecoratedEventHandler build() {
      Objects.requireNonNull(delegate, "delegate");
      return new DecoratedEventHandler(this);
    }
  }
}