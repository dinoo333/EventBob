package io.eventbob.dropwizard;

import io.eventbob.core.Capability;
import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import io.eventbob.dropwizard.adapter.RemoteCapability;
import io.eventbob.dropwizard.handlers.HealthcheckHandler;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventBobBundleTest {

    @Test
    void shouldLoadBothJarAndRemoteHandlers() throws Exception {
        List<RemoteCapability> remoteCapabilities = List.of(
            new RemoteCapability("remote-upper", URI.create("http://localhost:8080"))
        );

        EventBobBundle bundle = new EventBobBundle(null, null, remoteCapabilities);
        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();

        EventBob eventBob = bundle.eventBob(healthcheckHandler, httpClient);

        assertThat(eventBob).isNotNull();
    }

    @Test
    void shouldHandleNullRemoteCapabilities() throws Exception {
        EventBobBundle bundle = new EventBobBundle(null, null, null);

        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();
        EventBob eventBob = bundle.eventBob(healthcheckHandler, httpClient);

        assertThat(eventBob).isNotNull();
    }

    @Test
    void shouldCreateHttpClient() {
        EventBobBundle bundle = new EventBobBundle(null, null, null);

        HttpClient httpClient = bundle.httpClient();

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void shouldCreateHealthcheckHandler() {
        EventBobBundle bundle = new EventBobBundle(null, null, null);

        assertThat(bundle.healthcheckHandler()).isNotNull();
    }

    @Test
    void shouldHandleEmptyHandlerJarPaths() throws Exception {
        EventBobBundle bundle = new EventBobBundle(List.of(), null, List.of());
        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();

        EventBob eventBob = bundle.eventBob(healthcheckHandler, httpClient);

        assertThat(eventBob).isNotNull();
    }

    @Test
    void shouldRegisterRemoteHandlersInEventBob() throws Exception {
        List<RemoteCapability> remoteCapabilities = List.of(
            new RemoteCapability("remote-echo", URI.create("http://localhost:9000")),
            new RemoteCapability("remote-upper", URI.create("http://localhost:9001"))
        );

        EventBobBundle bundle = new EventBobBundle(null, null, remoteCapabilities);
        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();

        EventBob eventBob = bundle.eventBob(healthcheckHandler, httpClient);

        assertThat(eventBob).isNotNull();
    }

    @Test
    void shouldRegisterInlineLifecycleHandlers() throws Exception {
        EventBobBundle bundle = new EventBobBundle(null, List.of(new StubLifecycle()), null);
        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();

        EventBob eventBob = bundle.eventBob(healthcheckHandler, httpClient);

        assertThat(eventBob).isNotNull();
    }

    @Test
    void shouldThrowOnDuplicateCapabilityAcrossInlineAndRemote() {
        List<RemoteCapability> remoteCapabilities = List.of(
            new RemoteCapability("stub", URI.create("http://localhost:9000"))
        );
        EventBobBundle bundle = new EventBobBundle(null, List.of(new StubLifecycle()), remoteCapabilities);
        HttpClient httpClient = bundle.httpClient();
        HealthcheckHandler healthcheckHandler = bundle.healthcheckHandler();

        assertThatThrownBy(() -> bundle.eventBob(healthcheckHandler, httpClient))
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
