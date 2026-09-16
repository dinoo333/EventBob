package io.eventbob.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SyncForwardingEventHandlerTest {

  private static Event event(String payload) {
    return Event.builder().source("source").target("target").payload(payload).build();
  }

  private static SyncForwardingEventHandler<String, String> handlerOf(
      BiFunction<SyncForwardingEventHandler<String, String>, Event, String> requestBuilder,
      BiFunction<SyncForwardingEventHandler<String, String>, String, Event> responseParser,
      BiFunction<SyncForwardingEventHandler<String, String>, String, String> delegate) {
    return new SyncForwardingEventHandler<>(requestBuilder, responseParser, delegate) {};
  }

  @Test
  void handleForwardsRequestThroughDelegateAndParsesResponse() {
    SyncForwardingEventHandler<String, String> handler = handlerOf(
        (h, e) -> "req:" + e.getPayload(),
        (h, resp) -> event(resp),
        (h, req) -> req + ":handled");

    Event result = handler.handle(event("in"), null);

    assertThat(result.getPayload()).isEqualTo("req:in:handled");
  }

  @Test
  void eventHandlingExceptionFromDelegatePropagatesUnchanged() {
    EventHandlingException thrown = new EventHandlingException("boom");
    SyncForwardingEventHandler<String, String> handler = handlerOf(
        (h, e) -> "req",
        (h, resp) -> event(resp),
        (h, req) -> {
          throw thrown;
        });

    assertThatThrownBy(() -> handler.handle(event("in"), null)).isSameAs(thrown);
  }

  @Test
  void unexpectedExceptionFromRequestBuilderIsWrapped() {
    SyncForwardingEventHandler<String, String> handler = handlerOf(
        (h, e) -> {
          throw new IllegalStateException("bad request");
        },
        (h, resp) -> event(resp),
        (h, req) -> req);

    assertThatThrownBy(() -> handler.handle(event("in"), null))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Unexpected sync handling error")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void unexpectedExceptionFromResponseParserIsWrapped() {
    SyncForwardingEventHandler<String, String> handler = handlerOf(
        (h, e) -> "req",
        (h, resp) -> {
          throw new RuntimeException("bad response");
        },
        (h, req) -> req);

    assertThatThrownBy(() -> handler.handle(event("in"), null))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Unexpected sync handling error")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  void collaboratorsReceiveTheHandlerInstanceAsFirstArgument() {
    AtomicReference<SyncForwardingEventHandler<String, String>> seenInRequestBuilder =
        new AtomicReference<>();
    AtomicReference<SyncForwardingEventHandler<String, String>> seenInDelegate =
        new AtomicReference<>();
    AtomicReference<SyncForwardingEventHandler<String, String>> seenInResponseParser =
        new AtomicReference<>();

    SyncForwardingEventHandler<String, String> handler = handlerOf(
        (h, e) -> {
          seenInRequestBuilder.set(h);
          return "req";
        },
        (h, resp) -> {
          seenInResponseParser.set(h);
          return event(resp);
        },
        (h, req) -> {
          seenInDelegate.set(h);
          return req;
        });

    handler.handle(event("in"), null);

    assertThat(seenInRequestBuilder.get()).isSameAs(handler);
    assertThat(seenInDelegate.get()).isSameAs(handler);
    assertThat(seenInResponseParser.get()).isSameAs(handler);
  }
}
