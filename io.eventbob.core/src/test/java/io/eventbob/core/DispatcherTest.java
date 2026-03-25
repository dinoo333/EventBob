package io.eventbob.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DispatcherTest {

  @Test
  void sendWithTimeoutReturnsResultWhenCompletedWithinTimeout() {
    Dispatcher dispatcher = (event, onError) ->
        CompletableFuture.completedFuture(
            event.toBuilder().payload("success").build());

    Event request = Event.builder()
        .source("test")
        .target("handler")
        .payload("request")
        .build();

    Event result = dispatcher.send(request, (err, evt) -> null, 1000);

    assertThat(result).isNotNull();
    assertThat(result.getPayload()).isEqualTo("success");
  }

  @Test
  void sendWithTimeoutThrowsEventHandlingExceptionOnTimeout() {
    Dispatcher dispatcher = (event, onError) -> {
      CompletableFuture<Event> future = new CompletableFuture<>();
      // Never complete - will timeout
      return future;
    };

    Event request = Event.builder()
        .source("test")
        .target("handler")
        .build();

    assertThatThrownBy(() -> dispatcher.send(request, (err, evt) -> null, 10))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Timeout waiting for response")
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
  }

  @Test
  void sendWithTimeoutRestoresInterruptFlagOnInterruptedException() {
    Dispatcher dispatcher = (event, onError) -> {
      CompletableFuture<Event> future = new CompletableFuture<>();
      // Interrupt the test thread after a short delay
      new Thread(() -> {
        try {
          Thread.sleep(10);
          Thread.currentThread().interrupt();
        } catch (InterruptedException ignored) {
        }
      }).start();
      return future;
    };

    Event request = Event.builder()
        .source("test")
        .target("handler")
        .build();

    Thread testThread = Thread.currentThread();
    Thread.interrupted(); // Clear any existing interrupt status

    // Interrupt the current thread to simulate InterruptedException
    testThread.interrupt();

    assertThatThrownBy(() -> dispatcher.send(request, (err, evt) -> null, 100))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Interrupted while sending event")
        .hasCauseInstanceOf(InterruptedException.class);

    // Verify interrupt flag was restored
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void sendWithTimeoutUnwrapsCauseFromExecutionException() {
    RuntimeException originalCause = new RuntimeException("Original error");

    Dispatcher dispatcher = (event, onError) ->
        CompletableFuture.failedFuture(originalCause);

    Event request = Event.builder()
        .source("test")
        .target("handler")
        .build();

    assertThatThrownBy(() -> dispatcher.send(request, (err, evt) -> null, 1000))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Failed to send event")
        .hasCause(originalCause);
  }

  @Test
  void sendWithTimeoutHandlesEventHandlingExceptionFromHandler() {
    EventHandlingException handlerException = new EventHandlingException("Handler failed");

    Dispatcher dispatcher = (event, onError) ->
        CompletableFuture.failedFuture(handlerException);

    Event request = Event.builder()
        .source("test")
        .target("handler")
        .build();

    assertThatThrownBy(() -> dispatcher.send(request, (err, evt) -> null, 1000))
        .isInstanceOf(EventHandlingException.class)
        .hasMessageContaining("Failed to send event")
        .hasCause(handlerException);
  }
}
