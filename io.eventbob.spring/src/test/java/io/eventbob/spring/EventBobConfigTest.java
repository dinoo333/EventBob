package io.eventbob.spring;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import io.eventbob.spring.adapter.RemoteCapability;
import io.eventbob.spring.handlers.HealthcheckHandler;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventBobConfigTest {

  @Test
  void shouldLoadBothJarAndRemoteHandlers() {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("remote-upper", URI.create("http://localhost:8080"))
    );

    EventBobConfig config = new EventBobConfig(null, null, remoteCapabilities);
    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();

    EventBob eventBob = config.eventBob(healthcheckHandler, httpClient);

    assertThat(eventBob).isNotNull();
  }

  @Test
  void shouldHandleNullRemoteCapabilities() {
    EventBobConfig config = new EventBobConfig(null, null, null);

    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();
    EventBob eventBob = config.eventBob(healthcheckHandler, httpClient);

    assertThat(eventBob).isNotNull();
  }

  @Test
  void shouldCreateHttpClientBean() {
    EventBobConfig config = new EventBobConfig(null, null, null);

    HttpClient httpClient = config.httpClient();

    assertThat(httpClient).isNotNull();
    assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
  }

  @Test
  void shouldCreateHealthcheckHandlerBean() {
    EventBobConfig config = new EventBobConfig(null, null, null);

    assertThat(config.healthcheckHandler()).isNotNull();
  }

  @Test
  void shouldHandleEmptyHandlerJarPaths() {
    EventBobConfig config = new EventBobConfig(List.of(), null, List.of());
    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();

    EventBob eventBob = config.eventBob(healthcheckHandler, httpClient);

    assertThat(eventBob).isNotNull();
  }

  @Test
  void shouldRegisterRemoteHandlersInEventBob() {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("remote-echo", URI.create("http://localhost:9000")),
        new RemoteCapability("remote-upper", URI.create("http://localhost:9001"))
    );

    EventBobConfig config = new EventBobConfig(null, null, remoteCapabilities);
    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();

    EventBob eventBob = config.eventBob(healthcheckHandler, httpClient);

    assertThat(eventBob).isNotNull();
  }

  @Test
  void shouldRegisterInlineLifecycleHandlers() {
    EventBobConfig config = new EventBobConfig(null, List.of(new StubLifecycle()), null);
    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();

    EventBob eventBob = config.eventBob(healthcheckHandler, httpClient);

    assertThat(eventBob).isNotNull();
  }

  @Test
  void shouldThrowOnDuplicateCapabilityAcrossInlineAndRemote() {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("stub", URI.create("http://localhost:9000"))
    );
    EventBobConfig config = new EventBobConfig(null, List.of(new StubLifecycle()), remoteCapabilities);
    HttpClient httpClient = config.httpClient();
    HealthcheckHandler healthcheckHandler = config.healthcheckHandler();

    assertThatThrownBy(() -> config.eventBob(healthcheckHandler, httpClient))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stub");
  }

  // --- stubs ---

  @Capability("stub")
  static class StubHandler implements EventHandler {
    @Override
    public Event handle(Event event, Dispatcher dispatcher) {
      return event;
    }
  }

  static class StubLifecycle extends HandlerLifecycle {
    private final StubHandler handler = new StubHandler();

    @Override
    public void initialize(LifecycleContext context) {}

    @Override
    public EventHandler getHandler() {
      return handler;
    }

    @Override
    public void shutdown() {}
  }
}
