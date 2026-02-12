package io.eventbob.core;

import io.eventbob.core.eventrouting.Event;
import io.eventbob.core.eventrouting.EventHandler;
import io.eventbob.core.eventrouting.HandlerNotFoundException;
import io.eventbob.core.eventrouting.EventHandlingRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventHandlingRouterTest {

  /**
   * Simple recording stub.
   */
  private static final class RecordingHandler implements EventHandler {
    Event received;
    Event toReturn;

    RecordingHandler() {}
    RecordingHandler(Event toReturn) { this.toReturn = toReturn; }

    @Override
    public Event handle(Event event) {
      this.received = event;
      return toReturn != null ? toReturn : event;
    }
  }

  @Test
  @DisplayName("Given an event, when handling, then it is routed by target to the specific handler")
  void routesToExpectedHandler() throws Exception {
    RecordingHandler h1 = new RecordingHandler();
    RecordingHandler h2 = new RecordingHandler();

    EventHandlingRouter router = EventHandlingRouter.builder()
        .handler("alpha", h1)
        .handler("beta", h2)
        .build();

    Event event = Event.builder()
        .source("svc")
        .target("alpha")
        .payload("p")
        .build();

    Event result = router.handle(event);

    assertThat(h1.received).isSameAs(event);
    assertThat(h2.received).isNull();
    assertThat(result).isSameAs(event);
  }

  @Test
  @DisplayName("Given an event, when no handler is registered for the target, then a HandlerNotFoundException is thrown")
  void noHandlerThrowsHandlerNotFound() throws Exception {
    EventHandlingRouter router = EventHandlingRouter.builder()
        .handler("known", e -> e)
        .build();

    Event event = Event.builder()
        .source("svc")
        .target("missing")
        .build();

    HandlerNotFoundException ex =
        assertThrows(HandlerNotFoundException.class, () -> router.handle(event));

    assertThat(ex.getTarget()).isEqualTo("missing");
    assertThat(ex.getPayload()).isSameAs(event);
  }

  @Test
  @DisplayName("Given a null event, when handling, then a NullPointerException is thrown")
  void nullEventThrowsNullPointer() {
    EventHandlingRouter router = EventHandlingRouter.builder().build();
    assertThrows(NullPointerException.class, () -> router.handle(null));
  }
}