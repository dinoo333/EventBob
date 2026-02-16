package io.eventbob.core.eventrouting;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultErrorEventTest {

  @Test
  void createsEventWithErrorInfoInPayload() {
    Throwable error = new RuntimeException("Database connection failed");
    Event original = Event.builder()
        .source("inventory-service")
        .target("database-service")
        .build();

    Event errorEvent = DefaultErrorEvent.create(error, original);

    Map<String, Serializable> payload = (Map<String, Serializable>) errorEvent.getPayload();
    assertThat(payload).containsKey("errorMessage");
    assertThat(payload).containsKey("errorType");
    assertThat(payload).containsKey("originalEvent");

    assertThat(payload.get("errorMessage")).isEqualTo("Database connection failed");
    assertThat(payload.get("errorType")).isEqualTo("java.lang.RuntimeException");
    assertThat(payload.get("originalEvent")).isEqualTo(original);
  }

  @Test
  void preservesOriginalEventFields() {
    Map<String, Serializable> params = new LinkedHashMap<>();
    params.put("retryCount", "3");
    Map<String, Serializable> meta = new LinkedHashMap<>();
    meta.put("traceId", "trace-123");

    Event original = Event.builder()
        .source("api-gateway")
        .target("auth-service")
        .parameters(params)
        .metadata(meta)
        .payload("original-payload")
        .build();

    Throwable error = new IllegalArgumentException("Invalid token");
    Event errorEvent = DefaultErrorEvent.create(error, original);

    assertThat(errorEvent.getSource()).isEqualTo("api-gateway");
    assertThat(errorEvent.getTarget()).isEqualTo("auth-service");
    assertThat(errorEvent.getParameters()).containsExactly(entry("retryCount", "3"));
    assertThat(errorEvent.getMetadata()).containsExactly(entry("traceId", "trace-123"));
  }

  @Test
  void replacesPayloadWithErrorInfo() {
    Event original = Event.builder()
        .source("service-a")
        .target("service-b")
        .payload("original-payload-data")
        .build();

    Throwable error = new NullPointerException("Unexpected null value");
    Event errorEvent = DefaultErrorEvent.create(error, original);

    assertThat(errorEvent.getPayload()).isNotEqualTo("original-payload-data");
    assertThat(errorEvent.getPayload()).isInstanceOf(Map.class);

    Map<String, Serializable> payload = (Map<String, Serializable>) errorEvent.getPayload();
    assertThat(payload).containsKeys("errorMessage", "errorType", "originalEvent");
  }

  @Test
  void errorInfoStructureIsComplete() {
    Event original = Event.builder()
        .source("client")
        .target("server")
        .build();

    Throwable error = new IllegalStateException("Service unavailable");
    Event errorEvent = DefaultErrorEvent.create(error, original);

    Map<String, Serializable> payload = (Map<String, Serializable>) errorEvent.getPayload();

    assertThat(payload.get("errorMessage"))
        .isInstanceOf(String.class)
        .isEqualTo("Service unavailable");

    assertThat(payload.get("errorType"))
        .isInstanceOf(String.class)
        .isEqualTo("java.lang.IllegalStateException");

    assertThat(payload.get("originalEvent"))
        .isInstanceOf(Event.class)
        .isEqualTo(original);
  }

  @Test
  void handlesErrorWithNullMessage() {
    Event original = Event.builder()
        .source("svc-a")
        .target("svc-b")
        .build();

    Throwable error = new RuntimeException((String) null);

    // Null-safe: error.getMessage() can return null, should handle gracefully
    Event errorEvent = DefaultErrorEvent.create(error, original);

    Map<String, Serializable> payload = (Map<String, Serializable>) errorEvent.getPayload();
    assertThat(payload.get("errorMessage")).isEqualTo("null");
    assertThat(payload.get("errorType")).isEqualTo("java.lang.RuntimeException");
    assertThat(payload.get("originalEvent")).isEqualTo(original);
  }

  @Test
  void preservesEmptyParametersAndMetadata() {
    Event original = Event.builder()
        .source("svc-1")
        .target("svc-2")
        .build();

    Throwable error = new Exception("Error occurred");
    Event errorEvent = DefaultErrorEvent.create(error, original);

    assertThat(errorEvent.getParameters()).isEmpty();
    assertThat(errorEvent.getMetadata()).isEmpty();
  }

  @Test
  void errorEventIsIndependentOfOriginal() {
    Map<String, Serializable> params = new LinkedHashMap<>();
    params.put("key", "value");

    Event original = Event.builder()
        .source("source-svc")
        .target("target-svc")
        .parameters(params)
        .build();

    Throwable error = new RuntimeException("Test error");
    Event errorEvent = DefaultErrorEvent.create(error, original);

    params.put("key", "modified");

    assertThat(errorEvent.getParameters()).containsExactly(entry("key", "value"));
  }
}
