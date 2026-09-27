package io.eventbob.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventBuilderTest {

  private static Event template() {
    return Event
        .builder()
        .source("template-source")
        .target("template-target")
        .build();
  }

  @Test
  void policyRunsForMatchingRouteAndMutationIsReflected() {
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a", "b", eb -> eb.getParameters().put("touched", "yes"))
        .build();

    Event result = builder.build(template(), "a", "b", "payload");

    assertThat(result.getParameters()).containsEntry("touched", "yes");
    assertThat(result.getSource()).isEqualTo("a");
    assertThat(result.getTarget()).isEqualTo("b");
    assertThat(result.getPayload()).isEqualTo("payload");
  }

  @Test
  void multiplePoliciesForSameRouteAccumulateAndRunInRegistrationOrder() {
    List<String> order = new ArrayList<>();
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a", "b", eb -> order.add("first"))
        .policy("a", "b", eb -> order.add("second"))
        .build();

    builder.build(template(), "a", "b", null);

    assertThat(order).containsExactly("first", "second");
  }

  @Test
  void differentRoutesStayIndependentDespiteConcatenationCollision() {
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a/b", "c", eb -> eb.getParameters().put("route", "a/b|c"))
        .policy("a", "b/c", eb -> eb.getParameters().put("route", "a|b/c"))
        .build();

    Event result = builder.build(template(), "a/b", "c", null);

    assertThat(result.getParameters())
        .containsExactly(Map.entry("route", "a/b|c"));
  }

  @Test
  void unmatchedRouteBuildIsNoOp() {
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a", "b", eb -> eb.getParameters().put("touched", "yes"))
        .build();

    Event result = builder.build(template(), "x", "y", "payload");

    assertThat(result.getParameters()).isEmpty();
    assertThat(result.getSource()).isEqualTo("x");
    assertThat(result.getTarget()).isEqualTo("y");
    assertThat(result.getPayload()).isEqualTo("payload");
  }

  @Test
  void builderReuseAfterBuildDoesNotAffectAlreadyBuiltEventBuilder() {
    EventBuilder.Builder builderBuilder = EventBuilder.builder()
        .policy("a", "b", eb -> eb.getParameters().put("first", "policy"));
    EventBuilder alreadyBuilt = builderBuilder.build();

    builderBuilder.policy("a", "b", eb -> eb.getParameters().put("second", "policy"));

    Event result = alreadyBuilt.build(template(), "a", "b", null);

    assertThat(result.getParameters()).containsExactly(Map.entry("first", "policy"));
  }

  @Test
  void policyRegistrationFailsFastOnInvalidArguments() {
    EventBuilder.Builder builder = EventBuilder.builder();

    assertThatThrownBy(() -> builder.policy(null, "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.policy("", "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.policy("s", null, eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");

    assertThatThrownBy(() -> builder.policy("s", "t", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void adviceMutatesParametersAndMetadataVisibleInBuiltEvent() {
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a", "b", eb -> {
          eb.getParameters().put("p1", "v1");
          eb.getMetadata().put("m1", "v1");
        })
        .build();

    Event result = builder.build(template(), "a", "b", null);

    assertThat(result.getParameters()).containsExactly(Map.entry("p1", "v1"));
    assertThat(result.getMetadata()).containsExactly(Map.entry("m1", "v1"));
  }

  @Test
  void laterPolicyReplaceClobbersEarlierPolicyGetterMutation() {
    EventBuilder builder = EventBuilder
        .builder()
        .policy("a", "b", eb -> eb.getParameters().put("a", "1"))
        .policy("a", "b", eb -> eb.parameters(Map.of("b", "2")))
        .build();

    Event result = builder.build(template(), "a", "b", null);

    assertThat(result.getParameters()).containsExactly(Map.entry("b", "2"));
  }
}
