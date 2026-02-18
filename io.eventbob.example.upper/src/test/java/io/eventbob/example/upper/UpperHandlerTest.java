package io.eventbob.example.upper;

import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpperHandlerTest {

  private UpperHandler handler;

  @BeforeEach
  void setUp() {
    handler = new UpperHandler();
  }

  @Test
  void shouldConvertLowercaseToUppercase() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("hello world")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldConvertMixedCaseToUppercase() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("HeLLo WoRLd")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldHandleAlreadyUppercaseInput() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("HELLO WORLD")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldHandleEmptyString() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("");
  }

  @Test
  void shouldSetSourceToUpper() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("test")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getSource()).isEqualTo("upper");
  }

  @Test
  void shouldSetTargetToOriginalSource() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("test")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getTarget()).isEqualTo("client");
  }

  @Test
  void shouldPreserveMetadata() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload("test")
        .metadata(Map.of("traceId", "abc123"))
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getMetadata()).containsEntry("traceId", "abc123");
  }

  @Test
  void shouldPreserveParameters() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .parameters(Map.of("locale", "en-US"))
        .payload("test")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getParameters()).containsEntry("locale", "en-US");
  }

  @Test
  void shouldThrowClassCastExceptionWhenPayloadIsNotString() {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .payload(12345)
        .build();

    assertThatThrownBy(() -> handler.handle(input, null))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldThrowNullPointerExceptionWhenPayloadIsNull() {
    Event input = Event.builder()
        .source("client")
        .target("upper")
        .build();

    assertThatThrownBy(() -> handler.handle(input, null))
        .isInstanceOf(NullPointerException.class);
  }
}
