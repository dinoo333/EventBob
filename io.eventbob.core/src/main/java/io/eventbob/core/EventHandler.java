package io.eventbob.core;

/**
 * EventHandler is the core interface for processing incoming events in EventBob. EventHandlers are
 * loaded dynamically from configured JARs and registered in the routing registry based on their
 * associated capabilities. (A JAR may contain one or more EventHandler implementations, each
 * supporting different capabilities and operations.)
 *
 * <p>Implementations of this interface contain the business logic for handling specific types of events.
 * Each handler is associated with a capability that defines what operations it supports.
 *
 * <p>When an event is received, the EventBob routes it to the appropriate EventHandler based on
 * the event's characteristics (e.g., service, capability, operation). The handler processes the event
 * and returns a response event that is sent back to the caller.
 */
public interface EventHandler {
  /**
   * Handles an incoming event and produces a response event.
   *
   * <p>Implementations of this method should contain the business logic for processing the event
   * and generating an appropriate response. The dispatcher can be used to send additional events
   * if needed.
   *
   * @param event The incoming event to handle.
   * @param dispatcher The dispatcher to use for sending events if necessary.
   * @return The response event to be sent back to the caller.
   * @throws EventHandlingException If an error occurs during event handling.
   */
  Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException;
}
