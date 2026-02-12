package io.eventbob.core;

import io.eventbob.core.eventrouting.Event;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventTest {

  @Test
  void buildAndAccessors() {
    Map<String, Serializable> path = new LinkedHashMap<>();
    path.put("id", "123");
    Map<String, Serializable> params = new LinkedHashMap<>();
    params.put("q", "search");
    Map<String, Serializable> meta = new LinkedHashMap<>();
    meta.put("traceId", "abc");

    Event e = Event.builder()
        .source("inventory-service")
        .target("item-service")
        .parameters(params)
        .metadata(meta)
        .payload("payload-body")
        .build();

    assertThat(e.getSource()).isEqualTo("inventory-service");
    assertThat(e.getTarget()).isEqualTo("item-service");
    assertThat(e.getParameters()).containsExactly(entry("q", "search"));
    assertThat(e.getMetadata()).containsExactly(entry("traceId", "abc"));
    assertThat(e.getPayload()).isEqualTo("payload-body");
  }

  @Test
  void requiredFieldsValidation() {
    assertThatThrownBy(() ->
        Event.builder().source(" ").target("T").build()
    ).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() ->
        Event.builder().source("S").target(" ").build()
    ).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");
  }

  @Test
  void mapsAreDefensivelyCopiedAndImmutable() {
    Map<String, Serializable> path = new LinkedHashMap<>();
    path.put("id", "1");
    Map<String, Serializable> params = new LinkedHashMap<>();
    params.put("p", "v");

    Event e = Event.builder()
        .source("svc")
        .target("target")
        .parameters(params)
        .build();

    // Mutate originals
    path.put("id", "CHANGED");
    params.put("p2", "x");
    assertThat(e.getParameters()).containsExactly(entry("p", "v"));

    assertThatThrownBy(() -> e.getParameters().remove("p"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> e.getMetadata().put("k", "v"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void emptyOrNullMapsResultInEmptyUnmodifiableMaps() {
    Event e = Event.builder()
        .source("svc")
        .target("target")
        .parameters(null)
        .metadata(null)
        .build();

    assertThat(e.getParameters()).isEmpty();
    assertThat(e.getMetadata()).isEmpty();
  }

  @Test
  void toBuilderProducesIndependentCopy() {
    Event original = Event.builder()
        .source("svc")
        .target("target")
        .metadata(Map.of("traceId", "t1"))
        .payload("data")
        .build();

    Event modified = original.toBuilder()
        .metadata(new LinkedHashMap<>(Map.of("traceId", (Serializable) "t2")))
        .payload("new-data")
        .build();
    assertThat(original.getMetadata()).containsExactly(entry("traceId", "t1"));
    assertThat(original.getPayload()).isEqualTo("data");
    assertThat(modified.getMetadata()).containsExactly(entry("traceId", "t2"));
    assertThat(modified.getPayload()).isEqualTo("new-data");
  }

  @Test
  void equalsAndHashCodeConsistency() {
    Event e1 = Event.builder()
        .source("svc")
        .target("target")
        .payload("p")
        .build();

    Event e2 = e1.toBuilder().build();

    assertThat(e2).isEqualTo(e1)
        .hasSameHashCodeAs(e1);
  }

  @Test
  void toStringContainsKeyFields() {
    Event e = Event.builder()
        .source("svc")
        .target("target")
        .payload("payload")
        .build();

    String s = e.toString();
    assertThat(s).contains("svc");
    assertThat(s).contains("target");
    assertThat(s).contains("payload");
  }

  @Test
  void nullPayloadAllowed() {
    Event e = Event.builder()
        .source("svc")
        .target("target")
        .build();

    assertThat(e.getPayload()).isNull();
  }
}