package io.eventbob.core;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveredHandlerTest {

    private static class TestHandler implements EventHandler {
        @Override
        public Event handle(Event event, Dispatcher dispatcher) {
            return event; // Test stub implementation
        }
    }

    @Test
    void constructor_withValidInputs_createsRecord() {
        DiscoveredHandler handler = new DiscoveredHandler("test", TestHandler.class);

        assertThat(handler.capability()).isEqualTo("test");
        assertThat(handler.handlerClass()).isEqualTo(TestHandler.class);
    }

    @Test
    void constructor_withNullCapability_throwsException() {
        assertThatThrownBy(() -> new DiscoveredHandler(null, TestHandler.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Capability must not be null");
    }

    @Test
    void constructor_withBlankCapability_throwsException() {
        assertThatThrownBy(() -> new DiscoveredHandler("   ", TestHandler.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Capability must not be null or blank");
    }

    @Test
    void constructor_withNullHandlerClass_throwsException() {
        assertThatThrownBy(() -> new DiscoveredHandler("test", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Handler class must not be null");
    }
}
