package io.eventbob.core;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;
import io.eventbob.core.HandlerNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EventBobTest {

  @Test
  void routesToCorrectHandlerByTarget() throws Exception {
    EventHandler echoHandler = (event, dispatcher) ->
        event.toBuilder().payload("echo: " + event.getPayload()).build();

    EventHandler upperHandler = (event, dispatcher) ->
        event.toBuilder().payload(event.getPayload().toString().toUpperCase()).build();

    EventBob bob = EventBob.builder()
        .handler("echo", echoHandler)
        .handler("upper", upperHandler)
        .build();

    Event echoRequest = Event.builder()
        .source("test")
        .target("echo")
        .payload("hello")
        .build();

    Event upperRequest = Event.builder()
        .source("test")
        .target("upper")
        .payload("hello")
        .build();

    CompletableFuture<Event> echoResult = bob.processEvent(echoRequest, (err, evt) -> null);
    CompletableFuture<Event> upperResult = bob.processEvent(upperRequest, (err, evt) -> null);

    assertThat(echoResult.get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("echo: hello");
    assertThat(upperResult.get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("HELLO");
  }

  @Test
  void throwsHandlerNotFoundExceptionWhenTargetUnknown() throws Exception {
    EventBob bob = EventBob.builder()
        .handler("known", (event, dispatcher) -> event)
        .build();

    Event unknownTargetEvent = Event.builder()
        .source("test")
        .target("unknown")
        .build();

    CompletableFuture<Event> result = bob.processEvent(unknownTargetEvent, (err, evt) -> null);

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(errorResult).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) errorResult.getPayload();
    assertThat(errorPayload.get("errorType"))
        .asString()
        .contains("CompletionException");
    assertThat(errorPayload.get("errorMessage"))
        .asString()
        .contains("HandlerNotFoundException");
  }

  @Test
  void processEventReturnsCompletableFuture() {
    EventBob bob = EventBob.builder()
        .handler("test", (event, dispatcher) -> event)
        .build();

    Event event = Event.builder().source("s").target("test").build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> null);

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(CompletableFuture.class);
  }

  @Test
  void executesAsynchronouslyOnBackgroundThread() throws Exception {
    AtomicReference<String> executingThreadName = new AtomicReference<>();

    EventHandler captureThreadHandler = (event, dispatcher) -> {
      executingThreadName.set(Thread.currentThread().getName());
      return event;
    };

    EventBob bob = EventBob.builder()
        .handler("test", captureThreadHandler)
        .build();

    Event event = Event.builder().source("s").target("test").build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> null);
    result.get(1, TimeUnit.SECONDS);

    String testThreadName = Thread.currentThread().getName();
    assertThat(executingThreadName.get()).isNotNull();
    assertThat(executingThreadName.get()).isNotEqualTo(testThreadName);
  }

  @Test
  void invokesOnErrorCallbackWhenHandlerThrows() throws Exception {
    EventHandler failingHandler = (event, dispatcher) -> {
      throw new EventHandlingException("Simulated failure");
    };

    EventBob bob = EventBob.builder()
        .handler("fail", failingHandler)
        .build();

    Event event = Event.builder().source("s").target("fail").build();

    AtomicReference<Throwable> capturedError = new AtomicReference<>();
    AtomicReference<Event> capturedEvent = new AtomicReference<>();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> {
      capturedError.set(err);
      capturedEvent.set(evt);
      return evt.toBuilder().payload("error handled").build();
    });

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(capturedError.get()).isNotNull();
    assertThat(capturedError.get()).hasCauseInstanceOf(EventHandlingException.class);
    assertThat(capturedEvent.get()).isEqualTo(event);
    assertThat(errorResult.getPayload()).isEqualTo("error handled");
  }

  @Test
  void usesOnErrorReturnValueAsResult() throws Exception {
    EventHandler failingHandler = (event, dispatcher) -> {
      throw new EventHandlingException("Simulated failure");
    };

    EventBob bob = EventBob.builder()
        .handler("fail", failingHandler)
        .build();

    Event event = Event.builder().source("s").target("fail").build();

    Event customErrorEvent = Event.builder()
        .source("error-handler")
        .target("custom-error")
        .payload("custom error response")
        .build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> customErrorEvent);

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(errorResult).isEqualTo(customErrorEvent);
    assertThat(errorResult.getPayload()).isEqualTo("custom error response");
  }

  @Test
  void usesDefaultErrorEventWhenOnErrorReturnsNull() throws Exception {
    EventHandler failingHandler = (event, dispatcher) -> {
      throw new EventHandlingException("Simulated failure");
    };

    EventBob bob = EventBob.builder()
        .handler("fail", failingHandler)
        .build();

    Event originalEvent = Event.builder()
        .source("s")
        .target("fail")
        .payload("original payload")
        .build();

    CompletableFuture<Event> result = bob.processEvent(originalEvent, (err, evt) -> null);

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(errorResult).isNotNull();
    assertThat(errorResult.getSource()).isEqualTo("s");
    assertThat(errorResult.getTarget()).isEqualTo("fail");

    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) errorResult.getPayload();

    assertThat(errorPayload).containsKeys("errorMessage", "errorType", "originalEvent");
    assertThat(errorPayload.get("errorMessage"))
        .asString()
        .contains("EventHandlingException")
        .contains("Simulated failure");
    assertThat(errorPayload.get("errorType"))
        .asString()
        .contains("CompletionException");
    assertThat(errorPayload.get("originalEvent")).isEqualTo(originalEvent);
  }

  @Test
  void passesNonNullDispatcherToHandler() throws Exception {
    AtomicReference<Dispatcher> capturedDispatcher = new AtomicReference<>();

    EventHandler captureDispatcherHandler = (event, dispatcher) -> {
      capturedDispatcher.set(dispatcher);
      return event;
    };

    EventBob bob = EventBob.builder()
        .handler("test", captureDispatcherHandler)
        .build();

    Event event = Event.builder().source("s").target("test").build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> null);
    result.get(1, TimeUnit.SECONDS);

    assertThat(capturedDispatcher.get()).isNotNull();
  }

  @Test
  void handlerCanReDispatchEventsViaDispatcher() throws Exception {
    EventHandler upperHandler = (event, dispatcher) ->
        event.toBuilder().payload(event.getPayload().toString().toUpperCase()).build();

    EventHandler delegatingHandler = (event, dispatcher) -> {
      Event delegatedEvent = Event.builder()
          .source("delegating-handler")
          .target("upper")
          .payload(event.getPayload())
          .build();

      try {
        Event upperResult = dispatcher.send(delegatedEvent, (err, evt) -> null).get(1, TimeUnit.SECONDS);
        return event.toBuilder()
            .payload("delegated: " + upperResult.getPayload())
            .build();
      } catch (Exception e) {
        throw new EventHandlingException("Re-dispatch failed", e);
      }
    };

    EventBob bob = EventBob.builder()
        .handler("upper", upperHandler)
        .handler("delegating", delegatingHandler)
        .build();

    Event request = Event.builder()
        .source("test")
        .target("delegating")
        .payload("hello")
        .build();

    CompletableFuture<Event> result = bob.processEvent(request, (err, evt) -> null);

    Event finalResult = result.get(1, TimeUnit.SECONDS);

    assertThat(finalResult.getPayload()).isEqualTo("delegated: HELLO");
  }

  @Test
  void reDispatchedEventsRouteToCorrectHandlers() throws Exception {
    EventHandler echoHandler = (event, dispatcher) ->
        event.toBuilder().payload("echo: " + event.getPayload()).build();

    EventHandler chainHandler = (event, dispatcher) -> {
      Event echoRequest = Event.builder()
          .source("chain")
          .target("echo")
          .payload("chained")
          .build();

      try {
        Event echoResult = dispatcher.send(echoRequest, (err, evt) -> null).get(1, TimeUnit.SECONDS);
        return event.toBuilder()
            .payload("chain result: " + echoResult.getPayload())
            .build();
      } catch (Exception e) {
        throw new EventHandlingException("Chain failed", e);
      }
    };

    EventBob bob = EventBob.builder()
        .handler("echo", echoHandler)
        .handler("chain", chainHandler)
        .build();

    Event request = Event.builder().source("test").target("chain").build();

    CompletableFuture<Event> result = bob.processEvent(request, (err, evt) -> null);

    assertThat(result.get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("chain result: echo: chained");
  }

  @Test
  void builderValidatesTargetNotBlank() {
    EventBob.Builder builder = EventBob.builder();
    EventHandler dummyHandler = (event, dispatcher) -> event;

    assertThatThrownBy(() -> builder.handler(null, dummyHandler))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");

    assertThatThrownBy(() -> builder.handler("", dummyHandler))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");

    assertThatThrownBy(() -> builder.handler("   ", dummyHandler))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");
  }

  @Test
  void builderValidatesHandlerNotNull() {
    EventBob.Builder builder = EventBob.builder();

    assertThatThrownBy(() -> builder.handler("test", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("handler");
  }

  @Test
  void builderSupportsFluentChaining() {
    EventHandler handler = (event, dispatcher) -> event;

    EventBob.Builder builder = EventBob.builder()
        .handler("h1", handler)
        .handler("h2", handler)
        .handler("h3", handler);

    EventBob bob = builder.build();

    assertThat(bob).isNotNull();
  }

  @Test
  void multipleHandlersRoutedCorrectly() throws Exception {
    Map<String, AtomicReference<String>> executionLog = new LinkedHashMap<>();
    executionLog.put("h1", new AtomicReference<>());
    executionLog.put("h2", new AtomicReference<>());
    executionLog.put("h3", new AtomicReference<>());

    EventHandler h1 = (event, dispatcher) -> {
      executionLog.get("h1").set("executed");
      return event.toBuilder().payload("h1 result").build();
    };

    EventHandler h2 = (event, dispatcher) -> {
      executionLog.get("h2").set("executed");
      return event.toBuilder().payload("h2 result").build();
    };

    EventHandler h3 = (event, dispatcher) -> {
      executionLog.get("h3").set("executed");
      return event.toBuilder().payload("h3 result").build();
    };

    EventBob bob = EventBob.builder()
        .handler("h1", h1)
        .handler("h2", h2)
        .handler("h3", h3)
        .build();

    Event e1 = Event.builder().source("t").target("h1").build();
    Event e2 = Event.builder().source("t").target("h2").build();
    Event e3 = Event.builder().source("t").target("h3").build();

    assertThat(bob.processEvent(e1, (err, evt) -> null).get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("h1 result");
    assertThat(bob.processEvent(e2, (err, evt) -> null).get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("h2 result");
    assertThat(bob.processEvent(e3, (err, evt) -> null).get(1, TimeUnit.SECONDS).getPayload()).isEqualTo("h3 result");

    assertThat(executionLog.get("h1").get()).isEqualTo("executed");
    assertThat(executionLog.get("h2").get()).isEqualTo("executed");
    assertThat(executionLog.get("h3").get()).isEqualTo("executed");
  }

  @Test
  void handlerReturningNullProducesNullResult() throws Exception {
    EventHandler nullReturningHandler = (event, dispatcher) -> null;

    EventBob bob = EventBob.builder()
        .handler("null", nullReturningHandler)
        .build();

    Event event = Event.builder().source("s").target("null").build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> null);

    assertThat(result.get(1, TimeUnit.SECONDS)).isNull();
  }

  @Test
  void emptyEventBobThrowsHandlerNotFoundException() throws Exception {
    EventBob bob = EventBob.builder().build();

    Event event = Event.builder().source("s").target("any").build();

    CompletableFuture<Event> result = bob.processEvent(event, (err, evt) -> null);

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(errorResult).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> errorPayload = (Map<String, Object>) errorResult.getPayload();
    assertThat(errorPayload.get("errorType"))
        .asString()
        .contains("CompletionException");
    assertThat(errorPayload.get("errorMessage"))
        .asString()
        .contains("HandlerNotFoundException");
  }

  @Test
  void handlerNotFoundExceptionTriggersErrorPath() throws Exception {
    EventBob bob = EventBob.builder()
        .handler("exists", (event, dispatcher) -> event)
        .build();

    Event unknownTargetEvent = Event.builder()
        .source("test")
        .target("does-not-exist")
        .build();

    AtomicReference<Throwable> capturedError = new AtomicReference<>();

    CompletableFuture<Event> result = bob.processEvent(unknownTargetEvent, (err, evt) -> {
      capturedError.set(err);
      return evt.toBuilder().payload("handler not found error handled").build();
    });

    Event errorResult = result.get(1, TimeUnit.SECONDS);

    assertThat(capturedError.get()).isNotNull();
    assertThat(capturedError.get()).hasCauseInstanceOf(HandlerNotFoundException.class);
    assertThat(errorResult.getPayload()).isEqualTo("handler not found error handled");
  }
}
