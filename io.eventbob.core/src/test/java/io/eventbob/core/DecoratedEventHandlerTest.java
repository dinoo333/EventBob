package io.eventbob.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BDD-style tests for DecoratedEventHandler handle behavior.
 */
class DecoratedEventHandlerTest {

  private Event newEvent(String target) {
    // Assuming Event has a builder with these methods per prior usage.
    return Event.builder()
        .source("src")
        .target(target)
        .payload("payload")
        .build();
  }

  @Test
  @DisplayName("Given before/after hooks, when handle succeeds, then they run in order around the delegate")
  void beforeAfterOrder() throws Exception {
    List<String> order = new ArrayList<>();
    EventHandler delegate = event -> {
      order.add("delegate");
      return event;
    };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .before(e -> order.add("before"))
        .afterSuccess((in, out) -> order.add("after"))
        .build();

    handler.handle(newEvent("t"));

    assertEquals(List.of("before", "delegate", "after"), order);
  }

  @Test
  @DisplayName("Given an error and no error handler, when delegate throws EventHandlingException, then onError runs and exception propagates")
  void errorRethrowEventHandlingException() {
    EventHandlingException boom = new EventHandlingException("fail");
    EventHandler delegate = e -> { throw boom; };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .build();

    Event event = newEvent("x");
    EventHandlingException ex = assertThrows(EventHandlingException.class, () -> handler.handle(event));
    assertSame(boom.getMessage(), ex.getMessage());
  }

  @Test
  @DisplayName("Given an error and no error handler, when delegate throws a non EventHandlingException, then onError runs and exception is wrapped and propagates")
  void errorRethrowPackagedEventHandlingException() {
    IllegalStateException boom = new IllegalStateException("fail");
    EventHandler delegate = e -> { throw boom; };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .build();

    Event event = newEvent("x");
    EventHandlingException ex = assertThrows(UnexpectedEventHandlingException.class, () -> handler.handle(event));
    assertSame(boom.getMessage(), ex.getCause().getMessage());
  }

  @Test
  @DisplayName("Given an error and an non-throwing error handler, when delegate throws EventHandlingException, then onError runs and exception is swallowed")
  void errorSwallowEventHandlingException() {
    AtomicReference<Throwable> captured = new AtomicReference<>();
    EventHandler delegate = e -> { throw new EventHandlingException("boom"); };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .onError((e, t) -> {
          captured.set(t);
          // swallow by returning the same event
          return e;
        })
        .build();

    handler.handle(newEvent("x")); // should not throw
    assertNotNull(captured.get());
    assertInstanceOf(EventHandlingException.class, captured.get());
  }

  @Test
  @DisplayName("Given an error and throwing error handler, when delegate throws RuntimeException, then onError runs and runtime propagates")
  void errorRethrowRuntimeException() {
    RuntimeException boom = new IllegalStateException("bad");
    AtomicReference<Throwable> captured = new AtomicReference<>();
    EventHandler delegate = e -> { throw boom; };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .onError((e, t) -> {
          captured.set(t);
          throw new IllegalStateException("bad", t);
        })
        .build();

    RuntimeException ex = assertThrows(IllegalStateException.class, () -> handler.handle(newEvent("x")));
    assertSame(boom.getMessage(), ex.getMessage());
    assertSame(boom, captured.get());
  }

  @Test
  @DisplayName("Given afterSuccess hook, when delegate throws, then afterSuccess is not invoked")
  void afterSuccessNotCalledOnFailure() {
    AtomicBoolean afterCalled = new AtomicBoolean(false);
    EventHandler delegate = e -> { throw new EventHandlingException("fail"); };

    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(delegate)
        .afterSuccess((in, out) -> afterCalled.set(true))
        .build();

    assertThrows(EventHandlingException.class, () -> handler.handle(newEvent("t")));
    assertFalse(afterCalled.get());
  }

  @Test
  @DisplayName("Given null event, when handle is invoked, then NullPointerException is thrown before hooks")
  void nullEvent() {
    AtomicBoolean beforeCalled = new AtomicBoolean(false);
    DecoratedEventHandler handler = DecoratedEventHandler.builder()
        .delegate(e -> e)
        .before(e -> beforeCalled.set(true))
        .build();

    assertThrows(NullPointerException.class, () -> handler.handle(null));
    assertFalse(beforeCalled.get());
  }
}