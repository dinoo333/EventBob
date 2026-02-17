package io.eventbob.core;

import io.eventbob.core.Event;
import io.eventbob.core.HandlerNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HandlerNotFoundExceptionTest {

  @Test
  void singleArgumentConstructorCreatesMessageWithTarget() {
    String target = "unknown-handler";

    HandlerNotFoundException exception = new HandlerNotFoundException(target);

    assertThat(exception.getMessage())
        .isEqualTo("No handler registered for target: unknown-handler");
  }

  @Test
  void twoArgumentConstructorCreatesMessageWithTargetAndEvent() {
    String target = "unknown-handler";
    Event event = Event.builder()
        .source("source-service")
        .target(target)
        .build();

    HandlerNotFoundException exception = new HandlerNotFoundException(target, event);

    assertThat(exception.getMessage())
        .startsWith("No handler registered for target: unknown-handler; event = Event{")
        .contains("source='source-service'")
        .contains("target='unknown-handler'");
  }

  @Test
  void extendsRuntimeException() {
    HandlerNotFoundException exception = new HandlerNotFoundException("target");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  void messageFormatConsistencyBetweenConstructors() {
    String target = "test-target";
    Event event = Event.builder()
        .source("src")
        .target(target)
        .build();

    HandlerNotFoundException single = new HandlerNotFoundException(target);
    HandlerNotFoundException withEvent = new HandlerNotFoundException(target, event);

    assertThat(single.getMessage()).startsWith("No handler registered for target: test-target");
    assertThat(withEvent.getMessage()).startsWith("No handler registered for target: test-target");
    assertThat(withEvent.getMessage()).contains("; event = ");
  }

  @Test
  void twoArgumentConstructorIncludesEventToString() {
    Event event = Event.builder()
        .source("service-a")
        .target("service-b")
        .payload("test-payload")
        .build();

    HandlerNotFoundException exception = new HandlerNotFoundException("service-b", event);

    String eventString = event.toString();
    assertThat(exception.getMessage()).contains(eventString);
  }
}
