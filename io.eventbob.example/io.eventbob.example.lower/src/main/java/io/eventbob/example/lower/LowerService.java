package io.eventbob.example.lower;

import io.eventbob.core.Dispatcher;
import io.eventbob.core.Event;
import io.eventbob.core.EventBuilder;

/**
 * Service that implements lowercase transformation logic.
 *
 * <p>This demonstrates the service layer pattern for extracting business logic
 * from handlers. In real applications, services would contain complex business
 * logic, database access, or external API calls.
 * </p>
 */
public class LowerService {
  /**
   * Transforms input string to lowercase.
   *
   * @param event      The lower event
   * @param eventBuilder the event builder
   * @param dispatcher the dispatcher (not used by this method)
   * @return lowercase version of input
   */
  public Event processLowercase(Event event, EventBuilder eventBuilder, Dispatcher dispatcher) {
    return eventBuilder.build(event, "lower", event.getSource(),
        ((String) event.getPayload()).toLowerCase());
  }
}
