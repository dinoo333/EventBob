package io.eventbob.core.eventrouting;

import io.eventbob.core.endpointresolution.Capability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an EventHandler implementation with its capability metadata.
 *
 * <p>This annotation is scanned by EventBob during JAR loading to discover
 * available capabilities and register them in the routing registry.
 *
 * <p>Example usage:
 * <pre>{@code
 * @EventHandlerCapability(
 *   service = "messages",
 *   capability = Capability.READ,
 *   capabilityVersion = 2,
 *   operations = {"GET /content", "GET /bulk-content"}
 * )
 * public class GetMessageContentHandler implements EventHandler {
 *   @Override
 *   public Event handle(Event event) {
 *     // Implementation
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandlerCapability {

    /**
     * Service name this handler belongs to.
     *
     * <p>Example: "messages", "user-service", "inventory"
     */
    String service();

    /**
     * Capability type this handler provides.
     */
    Capability capability();

    /**
     * Version of this capability contract.
     *
     * <p>Increment when operation signatures change (new fields, different semantics).
     * Must be positive integer.
     */
    int capabilityVersion() default 1;

    /**
     * Operations this handler implements.
     *
     * <p>Format: "{METHOD} {path-pattern}"
     * <p>Examples:
     * <ul>
     *   <li>"GET /content"</li>
     *   <li>"POST /create"</li>
     *   <li>"GET /user/{userId}/orders"</li>
     * </ul>
     */
    String[] operations();
}
