package io.eventbob.dropwizard.handlers;

import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthcheckHandlerTest {

    private HealthcheckHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HealthcheckHandler();
    }

    @Test
    void shouldReturnTruePayload() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getPayload()).isEqualTo(true);
    }

    @Test
    void shouldPreserveEventSource() throws EventHandlingException {
        Event input = Event.builder()
            .source("monitoring-system")
            .target("healthcheck")
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getSource()).isEqualTo("monitoring-system");
    }

    @Test
    void shouldPreserveEventTarget() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getTarget()).isEqualTo("healthcheck");
    }

    @Test
    void shouldPreserveMetadata() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .metadata(Map.of("traceId", "abc123", "spanId", "xyz789"))
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getMetadata())
            .containsEntry("traceId", "abc123")
            .containsEntry("spanId", "xyz789");
    }

    @Test
    void shouldPreserveParameters() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .parameters(Map.of("timeout", "5000"))
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getParameters()).containsEntry("timeout", "5000");
    }

    @Test
    void shouldIgnoreInputPayload() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .payload("some-data")
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getPayload()).isEqualTo(true);
    }

    @Test
    void shouldHandleNullPayload() throws EventHandlingException {
        Event input = Event.builder()
            .source("client")
            .target("healthcheck")
            .build();

        Event result = handler.handle(input, null);

        assertThat(result.getPayload()).isEqualTo(true);
    }

    @Test
    void shouldAlwaysReturnTrueRegardlessOfInput() throws EventHandlingException {
        Event input1 = Event.builder()
            .source("system-a")
            .target("healthcheck")
            .payload(false)
            .build();

        Event input2 = Event.builder()
            .source("system-b")
            .target("healthcheck")
            .payload(12345)
            .build();

        Event result1 = handler.handle(input1, null);
        Event result2 = handler.handle(input2, null);

        assertThat(result1.getPayload()).isEqualTo(true);
        assertThat(result2.getPayload()).isEqualTo(true);
    }
}
