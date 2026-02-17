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
  void capabilitiesAnnotationHasClassRetentionNotRuntime() {
    // @Capabilities does not have @Retention(RUNTIME), so it defaults to CLASS retention
    // This means the annotation is not available at runtime via reflection
    assertThat(MultiCapabilityHandler.class.isAnnotationPresent(Capabilities.class))
        .isFalse();

    Capabilities container = MultiCapabilityHandler.class.getAnnotation(Capabilities.class);
    assertThat(container).isNull();
  }

  @Test
  void capabilitiesContainerStructureDefinedInSource() {
    // The @Capabilities annotation exists in source code and accepts an array of @Capability
    // This test verifies the annotation can be applied without compilation errors
    // Runtime reflection is not possible due to CLASS retention (not RUNTIME)
    assertThat(MultiCapabilityHandler.class).isNotNull();
    assertThat(MultiCapabilityHandler.class.getInterfaces())
        .contains(EventHandler.class);
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
