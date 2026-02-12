package io.eventbob.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MetadataKeysTest {

  @Test
  @DisplayName("Given standard metadata keys, when building an event, then metadata is populated correctly")
  void standardMetadataKeysWork() {
    String correlationId = UUID.randomUUID().toString();
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

    Event event = Event.builder()
        .source("rest-adapter")
        .target("inventory-service")
        .metadata(Map.of(
            MetadataKeys.METHOD, "POST",
            MetadataKeys.PATH, "update-available/{catalog}/{sku}",
            MetadataKeys.CORRELATION_ID, correlationId,
            MetadataKeys.TRACE_ID, traceId
        ))
        .parameters(Map.of(
            "catalog", "electronics",
            "sku", "ABC123"
        ))
        .build();

    assertThat(event.getMetadata().get(MetadataKeys.METHOD)).isEqualTo("POST");
    assertThat(event.getMetadata().get(MetadataKeys.PATH)).isEqualTo("update-available/{catalog}/{sku}");
    assertThat(event.getMetadata().get(MetadataKeys.CORRELATION_ID)).isEqualTo(correlationId);
    assertThat(event.getMetadata().get(MetadataKeys.TRACE_ID)).isEqualTo(traceId);

    // Path parameters go in parameters, not metadata
    assertThat(event.getParameters().get("catalog")).isEqualTo("electronics");
    assertThat(event.getParameters().get("sku")).isEqualTo("ABC123");
  }

  @Test
  @DisplayName("Given transport-specific metadata, when using namespace prefixes, then keys are namespaced correctly")
  void transportNamespacesWork() {
    Event event = Event.builder()
        .source("http-adapter")
        .target("user-service")
        .metadata(Map.of(
            MetadataKeys.METHOD, "GET",
            "http.status", "200",
            "http.content-type", "application/json",
            "grpc.service", "UserService",
            "queue.topic", "user-events"
        ))
        .build();

    // Standard metadata
    assertThat(event.getMetadata().get(MetadataKeys.METHOD)).isEqualTo("GET");

    // HTTP-specific metadata uses http. prefix
    assertThat(event.getMetadata().get("http.status")).isEqualTo("200");
    assertThat(event.getMetadata().get("http.content-type")).isEqualTo("application/json");

    // gRPC-specific metadata uses grpc. prefix
    assertThat(event.getMetadata().get("grpc.service")).isEqualTo("UserService");

    // Queue-specific metadata uses queue. prefix
    assertThat(event.getMetadata().get("queue.topic")).isEqualTo("user-events");
  }

  @Test
  @DisplayName("Given reply-to metadata, when building async request, then reply address is captured")
  void replyToMetadataWorks() {
    Event request = Event.builder()
        .source("macro-a.http-adapter")
        .target("payment-service")
        .metadata(Map.of(
            MetadataKeys.CORRELATION_ID, UUID.randomUUID().toString(),
            MetadataKeys.REPLY_TO, "macro-a.http-adapter-thread-5"
        ))
        .build();

    assertThat(request.getMetadata().get(MetadataKeys.REPLY_TO))
        .isEqualTo("macro-a.http-adapter-thread-5");
  }

  @Test
  @DisplayName("Given observability metadata, when event flows through system, then trace context is preserved")
  void observabilityMetadataWorks() {
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    String spanId = "00f067aa0ba902b7";

    Event event = Event.builder()
        .source("gateway")
        .target("user-service")
        .metadata(Map.of(
            MetadataKeys.TRACE_ID, traceId,
            MetadataKeys.SPAN_ID, spanId
        ))
        .build();

    assertThat(event.getMetadata().get(MetadataKeys.TRACE_ID)).isEqualTo(traceId);
    assertThat(event.getMetadata().get(MetadataKeys.SPAN_ID)).isEqualTo(spanId);
  }

  @Test
  @DisplayName("Given path template in metadata, when parameters provided separately, then template and values are distinct")
  void pathTemplateAndParametersAreDistinct() {
    Event event = Event.builder()
        .source("rest-adapter")
        .target("order-service")
        .metadata(Map.of(
            MetadataKeys.METHOD, "PUT",
            MetadataKeys.PATH, "orders/{orderId}/items/{itemId}"
        ))
        .parameters(Map.of(
            "orderId", "ORD-123",
            "itemId", "ITEM-456",
            "quantity", 5  // business data, not path parameter
        ))
        .build();

    // Path template in metadata
    assertThat(event.getMetadata().get(MetadataKeys.PATH))
        .isEqualTo("orders/{orderId}/items/{itemId}");

    // Actual values in parameters
    assertThat(event.getParameters().get("orderId")).isEqualTo("ORD-123");
    assertThat(event.getParameters().get("itemId")).isEqualTo("ITEM-456");
    assertThat(event.getParameters().get("quantity")).isEqualTo(5);
  }
}