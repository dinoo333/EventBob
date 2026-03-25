package io.eventbob.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare the capabilities of an EventHandler.
 * This is used for routing decisions and to ensure that handlers are correctly registered with
 * their capabilities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Capabilities {
  Capability [] value();
}
