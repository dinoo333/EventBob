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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.firstSentEvent).isNotNull();
    assertThat(dispatcher.firstSentEvent.getTarget()).isEqualTo("lower");
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.firstSentEvent).isNotNull();
    assertThat(dispatcher.firstSentEvent.getSource()).isEqualTo("echo");
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    handler.handle(input, dispatcher);

    assertThat(dispatcher.firstSentEvent).isNotNull();
    assertThat(dispatcher.firstSentEvent.getPayload()).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldReturnCombinedResultFromDispatcher() throws EventHandlingException {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("HELLO WORLD")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo("hello world HELLO WORLD");
  }

  @Test
  void shouldSetSourceToEcho() throws EventHandlingException {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("TEST")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getSource()).isEqualTo("echo");
  }

  @Test
  void shouldSetTargetToOriginalSource() throws EventHandlingException {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("TEST")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getTarget()).isEqualTo("client");
  }

  @Test
  void shouldNotPreserveOriginalEventMetadata() throws EventHandlingException {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("TEST")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getMetadata()).isEmpty();
  }

  @Test
  void shouldNotPreserveOriginalEventParameters() throws EventHandlingException {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("TEST")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getParameters()).isEmpty();
  }

  @Test
  void shouldThrowEventHandlingExceptionWhenLowerDispatcherFails() {
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
  void shouldThrowEventHandlingExceptionWhenLowerDispatcherTimesOut() {
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo(" ");
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

    Event upperResponse = Event.builder()
        .source("upper")
        .target("echo")
        .payload("12345")
        .build();

    RecordingDispatcher dispatcher = new RecordingDispatcher(lowerResponse, upperResponse);

    Event result = handler.handle(input, dispatcher);

    assertThat(result.getPayload()).isEqualTo("12345 12345");
  }

  /**
   * Test double that records the event sent and returns a pre-configured response.
   */
  private static class RecordingDispatcher implements Dispatcher {
    Event firstSentEvent;
    Event secondSentEvent;
    private final Event lowerResponse;
    private final Event upperResponse;
    private int callCount = 0;

    RecordingDispatcher(Event lowerResponse, Event upperResponse) {
      this.lowerResponse = lowerResponse;
      this.upperResponse = upperResponse;
    }

    @Override
    public CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError) {
      if (callCount == 0) {
        this.firstSentEvent = event;
        callCount++;
        return CompletableFuture.completedFuture(lowerResponse);
      } else {
        this.secondSentEvent = event;
        callCount++;
        return CompletableFuture.completedFuture(upperResponse);
      }
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
