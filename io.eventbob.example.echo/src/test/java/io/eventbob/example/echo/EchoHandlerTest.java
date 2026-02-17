package io.eventbob.example.echo;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EchoHandlerTest {

  private EchoHandler handler;

  @BeforeEach
  void setUp() {
    handler = new EchoHandler();
  }

  @Test
  void shouldCallDispatcherWithTargetLower() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("hello world")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.sentEvent).isNotNull();
    assertThat(dispatcher.sentEvent.getTarget()).isEqualTo("lower");
  }

  @Test
  void shouldCallDispatcherWithSourceEcho() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("hello world")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.sentEvent).isNotNull();
    assertThat(dispatcher.sentEvent.getSource()).isEqualTo("echo");
  }

  @Test
  void shouldPassInputPayloadToDispatcher() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("hello world")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.sentEvent).isNotNull();
    assertThat(dispatcher.sentEvent.getPayload()).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldReturnLowercasedResultFromDispatcher() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("hello world")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo("hello world");
  }

  @Test
  void shouldPreserveOriginalEventSource() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("TEST")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("test")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getSource()).isEqualTo("client");
  }

  @Test
  void shouldPreserveOriginalEventTarget() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("TEST")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("test")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getTarget()).isEqualTo("echo");
  }

  @Test
  void shouldPreserveOriginalEventMetadata() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .metadata(Map.of("traceId", "abc123"))
        .payload("TEST")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("test")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getMetadata()).containsEntry("traceId", "abc123");
  }

  @Test
  void shouldPreserveOriginalEventParameters() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .parameters(Map.of("locale", "en-US"))
        .payload("TEST")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("test")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getParameters()).containsEntry("locale", "en-US");
  }

  @Test
  void shouldThrowEventHandlingExceptionWhenDispatcherFails() {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("TEST")
        .build();

    FailingDispatcher dispatcher = new FailingDispatcher(new RuntimeException("Dispatcher error"));

    assertThatThrownBy(() -> handler.handle(input, dispatcher))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Failed to call lower")
        .cause()
        .cause()
        .hasMessageContaining("Dispatcher error");
  }

  @Test
  void shouldThrowEventHandlingExceptionWhenDispatcherTimesOut() {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("TEST")
        .build();

    TimeoutDispatcher dispatcher = new TimeoutDispatcher();

    assertThatThrownBy(() -> handler.handle(input, dispatcher))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Failed to call lower")
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldHandleEmptyStringPayload() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload("")
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo("");
  }

  @Test
  void shouldHandleNonStringPayload() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("echo")
        .payload(12345)
        .build();

    Event lowerResponse = Event.builder()
        .source("lower")
        .target("echo")
        .payload("12345")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo("12345");
  }

  /**
   * Test double that records the event sent and returns a pre-configured response.
   */
  private static class RecordingDispatcher implements Dispatcher {
    Event sentEvent;
    private final Event response;

    RecordingDispatcher(Event response) {
      this.response = response;
    }

    @Override
    public CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError) {
      this.sentEvent = event;
      return CompletableFuture.completedFuture(response);
    }
  }

  /**
   * Test double that fails with a pre-configured exception.
   */
  private static class FailingDispatcher implements Dispatcher {
    private final Exception failure;

    FailingDispatcher(Exception failure) {
      this.failure = failure;
    }

    @Override
    public CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  /**
   * Test double that never completes (simulates timeout).
   */
  private static class TimeoutDispatcher implements Dispatcher {
    @Override
    public CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError) {
      return new CompletableFuture<>();
    }
  }
}
