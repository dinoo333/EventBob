package io.eventbob.example.lower;

import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LowerHandlerTest {

  private LowerHandler handler;

  @BeforeEach
  void setUp() {
    // Create service with null dispatcher (not used by lower service)
    LowerService lowerService = new LowerService();
    handler = new LowerHandler(lowerService);
  }

  @Test
  void shouldConvertUppercaseToLowercase() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("HELLO WORLD")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("hello world");
  }

  @Test
  void shouldConvertMixedCaseToLowercase() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("HeLLo WoRLd")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("hello world");
  }

  @Test
  void shouldHandleAlreadyLowercaseInput() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("hello world")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("hello world");
  }

  @Test
  void shouldHandleEmptyString() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getPayload()).isEqualTo("");
  }

  @Test
  void shouldSetSourceToLower() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("TEST")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getSource()).isEqualTo("lower");
  }

  @Test
  void shouldSetTargetToOriginalSource() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload("TEST")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getTarget()).isEqualTo("client");
  }

  @Test
  void shouldPreserveMetadata() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .metadata(Map.of("traceId", "abc123"))
        .payload("TEST")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getMetadata()).containsEntry("traceId", "abc123");
  }

  @Test
  void shouldPreserveParameters() throws EventHandlingException {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .parameters(Map.of("locale", "en-US"))
        .payload("TEST")
        .build();

    Event result = handler.handle(input, null);

    assertThat(result.getParameters()).containsEntry("locale", "en-US");
  }

  @Test
  void shouldThrowClassCastExceptionWhenPayloadIsNotString() {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .payload(12345)
        .build();

    assertThatThrownBy(() -> handler.handle(input, null))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  void shouldThrowNullPointerExceptionWhenPayloadIsNull() {
    Event input = Event.builder()
        .source("client")
        .target("lower")
        .build();

    assertThatThrownBy(() -> handler.handle(input, null))
        .isInstanceOf(NullPointerException.class);
  }
}
