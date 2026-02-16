package io.eventbob.spring;

import io.eventbob.core.eventrouting.EventBob;
import io.eventbob.spring.handlers.HealthcheckHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for EventBob and its handlers.
 *
 * <p>Wires an EventBob instance with registered handlers as Spring beans.
 * Handlers are registered with their capability target identifiers.
 */
@Configuration
public class EventBobConfig {

  /**
   * Create the healthcheck handler bean.
   */
  @Bean
  public HealthcheckHandler healthcheckHandler() {
    return new HealthcheckHandler();
  }

  /**
   * Create the EventBob instance with registered handlers.
   *
   * @param healthcheckHandler The healthcheck handler bean.
   * @return Configured EventBob instance ready to process events.
   */
  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler) {
    return EventBob.builder()
        .handler("healthcheck", healthcheckHandler)
        .build();
  }
}
