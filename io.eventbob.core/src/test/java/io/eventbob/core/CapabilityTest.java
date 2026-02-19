package io.eventbob.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.core.Capabilities;
import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import io.eventbob.core.EventHandlingException;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;

class CapabilityTest {

  @Test
  void capabilityAnnotationCanBeAppliedToEventHandlerClass() {
    assertThat(SingleCapabilityHandler.class.isAnnotationPresent(Capability.class))
        .isTrue();
  }

  @Test
  void capabilityAnnotationHasRuntimeRetention() {
    Capability annotation = SingleCapabilityHandler.class.getAnnotation(Capability.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("get-user");
    assertThat(annotation.version()).isEqualTo(1);
  }

  @Test
  void capabilityAnnotationCanReadValueAttribute() {
    Capability annotation = SingleCapabilityHandler.class.getAnnotation(Capability.class);
    assertThat(annotation.value()).isEqualTo("get-user");
  }

  @Test
  void capabilityAnnotationCanReadVersionAttribute() {
    Capability annotation = VersionedCapabilityHandler.class.getAnnotation(Capability.class);
    assertThat(annotation.version()).isEqualTo(2);
  }

  @Test
  void capabilityVersionDefaultsToOne() {
    Capability annotation = SingleCapabilityHandler.class.getAnnotation(Capability.class);
    assertThat(annotation.version()).isEqualTo(1);
  }

  @Test
  void capabilitiesContainerAnnotationHasRuntimeRetention() {
    assertThat(MultiCapabilityHandler.class.isAnnotationPresent(Capabilities.class))
        .isTrue();

    Capabilities container = MultiCapabilityHandler.class.getAnnotation(Capabilities.class);
    assertThat(container).isNotNull();
    assertThat(container.value()).hasSize(3);
  }

  @Test
  void capabilitiesContainerHoldsCapabilityValues() {
    Capabilities container = MultiCapabilityHandler.class.getAnnotation(Capabilities.class);
    assertThat(container).isNotNull();

    Capability[] capabilities = container.value();
    assertThat(capabilities[0].value()).isEqualTo("get-message");
    assertThat(capabilities[0].version()).isEqualTo(1);
    assertThat(capabilities[1].value()).isEqualTo("create-message");
    assertThat(capabilities[1].version()).isEqualTo(1);
    assertThat(capabilities[2].value()).isEqualTo("create-message");
    assertThat(capabilities[2].version()).isEqualTo(2);
  }

  @Test
  void handlerWithoutCapabilitiesHasNoAnnotation() {
    assertThat(NoCapabilityHandler.class.isAnnotationPresent(Capability.class))
        .isFalse();
    assertThat(NoCapabilityHandler.class.isAnnotationPresent(Capabilities.class))
        .isFalse();
  }

  @Capability("get-user")
  static class SingleCapabilityHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
      return event;
    }
  }

  @Capability(value = "update-user", version = 2)
  static class VersionedCapabilityHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
      return event;
    }
  }

  @Capabilities({
      @Capability("get-message"),
      @Capability("create-message"),
      @Capability(value = "create-message", version = 2)
  })
  static class MultiCapabilityHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
      return event;
    }
  }

  static class NoCapabilityHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
      return event;
    }
  }
}
