package io.eventbob.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare a capability provided by an EventHandler.
 *
 * <pre>
 * &#64;Capabilities({
 *  &#64;Capability("get-message-content"),
 *  &#64;Capability("create-message", version = 1)
 *  &#64;Capability("create-message", version = 2)
 * })
 * public class MessageContentResource implements EventHandler {
 *   &#64;Override
 *   public Event handle(Event event, Dispatcher dispatcher) throws EventHandlingException {
 *     // Implementation
 *   }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Capabilities.class)
public @interface Capability {
  /**
   * Capability this handler provides.
   */
  String value();

  /**
   * Version of this capability contract.
   *
   * <p>Increment when operation signatures change (new fields, different semantics).
   * Must be positive integer.
   */
  int version() default 1;
}
