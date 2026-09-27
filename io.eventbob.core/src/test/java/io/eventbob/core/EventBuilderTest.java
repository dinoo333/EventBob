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
  void requestPolicyRunsForMatchingRouteAndMutationIsReflected() {
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("template-target", "b", eb -> eb.getParameters().put("touched", "yes"))
        .build();

    Event result = builder.request(template(), "b").payload("payload").build();

    assertThat(result.getParameters()).containsEntry("touched", "yes");
    assertThat(result.getSource()).isEqualTo("template-target");
    assertThat(result.getTarget()).isEqualTo("b");
    assertThat(result.getPayload()).isEqualTo("payload");
  }

  @Test
  void multipleRequestPoliciesForSameRouteAccumulateAndRunInRegistrationOrder() {
    List<String> order = new ArrayList<>();
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("template-target", "b", eb -> order.add("first"))
        .requestPolicy("template-target", "b", eb -> order.add("second"))
        .build();

    builder.request(template(), "b").build();

    assertThat(order).containsExactly("first", "second");
  }

  @Test
  void differentRequestRoutesStayIndependentDespiteConcatenationCollision() {
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("a/b", "c", eb -> eb.getParameters().put("route", "a/b|c"))
        .requestPolicy("a", "b/c", eb -> eb.getParameters().put("route", "a|b/c"))
        .build();

    Event fromAb = Event.builder().source("x").target("a/b").build();
    Event result = builder.request(fromAb, "c").build();

    assertThat(result.getParameters())
        .containsExactly(Map.entry("route", "a/b|c"));
  }

  @Test
  void unmatchedRequestRouteBuildIsNoOp() {
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("a", "b", eb -> eb.getParameters().put("touched", "yes"))
        .build();

    Event result = builder.request(template(), "y").payload("payload").build();

    assertThat(result.getParameters()).isEmpty();
    assertThat(result.getSource()).isEqualTo("template-target");
    assertThat(result.getTarget()).isEqualTo("y");
    assertThat(result.getPayload()).isEqualTo("payload");
  }

  @Test
  void builderReuseAfterBuildDoesNotAffectAlreadyBuiltEventBuilder() {
    EventBuilder.Builder builderBuilder = EventBuilder.builder()
        .requestPolicy("template-target", "b", eb -> eb.getParameters().put("first", "policy"));
    EventBuilder alreadyBuilt = builderBuilder.build();

    builderBuilder.requestPolicy("template-target", "b",
        eb -> eb.getParameters().put("second", "policy"));

    Event result = alreadyBuilt.request(template(), "b").build();

    assertThat(result.getParameters()).containsExactly(Map.entry("first", "policy"));
  }

  @Test
  void policyRegistrationFailsFastOnInvalidArguments() {
    EventBuilder.Builder builder = EventBuilder.builder();

    assertThatThrownBy(() -> builder.requestPolicy(null, "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.requestPolicy("", "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.requestPolicy("s", null, eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");

    assertThatThrownBy(() -> builder.requestPolicy("s", "t", null))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> builder.responsePolicy(null, "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.responsePolicy("", "t", eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source");

    assertThatThrownBy(() -> builder.responsePolicy("s", null, eb -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("target");

    assertThatThrownBy(() -> builder.responsePolicy("s", "t", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void requestPolicyMutatesParametersAndMetadataVisibleInBuiltEvent() {
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("template-target", "b", eb -> {
          eb.getParameters().put("p1", "v1");
          eb.getMetadata().put("m1", "v1");
        })
        .build();

    Event result = builder.request(template(), "b").build();

    assertThat(result.getParameters()).containsExactly(Map.entry("p1", "v1"));
    assertThat(result.getMetadata()).containsExactly(Map.entry("m1", "v1"));
  }

  @Test
  void laterRequestPolicyReplaceClobbersEarlierPolicyGetterMutation() {
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("template-target", "b", eb -> eb.getParameters().put("a", "1"))
        .requestPolicy("template-target", "b", eb -> eb.parameters(Map.of("b", "2")))
        .build();

    Event result = builder.request(template(), "b").build();

    assertThat(result.getParameters()).containsExactly(Map.entry("b", "2"));
  }

  @Test
  void responseSwapsSourceAndTargetAndRunsResponsePolicies() {
    EventBuilder builder = EventBuilder
        .builder()
        .responsePolicy("template-target", "template-source",
            eb -> eb.getParameters().put("touched", "yes"))
        .build();

    Event result = builder.response(template()).payload("payload").build();

    assertThat(result.getSource()).isEqualTo("template-target");
    assertThat(result.getTarget()).isEqualTo("template-source");
    assertThat(result.getParameters()).containsEntry("touched", "yes");
    assertThat(result.getPayload()).isEqualTo("payload");
  }

  @Test
  void requestPolicyDoesNotFireOnResponseForSameRouteAndViceVersa() {
    List<String> fired = new ArrayList<>();
    EventBuilder builder = EventBuilder
        .builder()
        .requestPolicy("template-target", "template-source", eb -> fired.add("request"))
        .responsePolicy("template-target", "template-source", eb -> fired.add("response"))
        .build();

    builder.request(template(), "template-source").build();
    assertThat(fired).containsExactly("request");

    fired.clear();
    builder.response(template()).build();
    assertThat(fired).containsExactly("response");
  }

  @Test
  void postHocSourceAndTargetOverrideDoesNotRerunPolicyResolution() {
    List<String> fired = new ArrayList<>();
    EventBuilder builder = EventBuilder
        .builder()
        .responsePolicy("template-target", "template-source", eb -> fired.add("original-route"))
        .responsePolicy("overridden-source", "overridden-target",
            eb -> fired.add("overridden-route"))
        .build();

    Event result = builder.response(template())
        .source("overridden-source")
        .target("overridden-target")
        .build();

    assertThat(fired).containsExactly("original-route");
    assertThat(result.getSource()).isEqualTo("overridden-source");
    assertThat(result.getTarget()).isEqualTo("overridden-target");
  }
}
