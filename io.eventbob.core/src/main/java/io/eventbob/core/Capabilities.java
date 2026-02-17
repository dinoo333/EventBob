package io.eventbob.core;

/**
 * Annotation to declare the capabilities of an EventHandler.
 * This is used for routing decisions and to ensure that handlers are correctly registered with
 * their capabilities.
 */
public @interface Capabilities {
  Capability [] value();
}
