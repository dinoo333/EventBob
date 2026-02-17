package io.eventbob.spring;

import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLoader;
import io.eventbob.spring.handlers.HealthcheckHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Spring configuration for EventBob and its handlers.
 * <p>
 * This configuration:
 * </p>
 * <ul>
 *   <li>Loads handlers from JAR files using the HandlerLoader</li>
 *   <li>Registers all handlers with EventBob</li>
 *   <li>Keeps the healthcheck handler hard-coded for system health monitoring</li>
 * </ul>
 * <p>
 * This is a generic library configuration. Concrete applications must provide
 * a {@code List<Path> handlerJarPaths} bean specifying which handler JARs to load.
 * </p>
 */
@Configuration
public class EventBobConfig {
  private static final Logger logger = LoggerFactory.getLogger(EventBobConfig.class);

  private final List<Path> handlerJarPaths;

  /**
   * Create EventBob configuration with specified handler JAR paths.
   *
   * @param handlerJarPaths list of paths to handler JAR files to load
   */
  public EventBobConfig(List<Path> handlerJarPaths) {
    this.handlerJarPaths = handlerJarPaths;
  }

  /**
   * Create the healthcheck handler bean.
   * <p>
   * This handler is hard-coded and not loaded from JARs, as it provides
   * core system health monitoring functionality.
   * </p>
   *
   * @return the healthcheck handler instance
   */
  @Bean
  public HealthcheckHandler healthcheckHandler() {
    return new HealthcheckHandler();
  }

  /**
   * Create the EventBob instance with all registered handlers.
   * <p>
   * This method:
   * </p>
   * <ol>
   *   <li>Loads and instantiates handlers from configured JAR files</li>
   *   <li>Registers handlers with their capability names</li>
   *   <li>Includes the healthcheck handler</li>
   * </ol>
   *
   * @param healthcheckHandler the healthcheck handler bean
   * @return configured EventBob instance ready to process events
   */
  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler) {
    EventBob.Builder builder = EventBob.builder();

    // Register hard-coded healthcheck handler
    builder.handler("healthcheck", healthcheckHandler);

    // Load and register JAR-based handlers
    try {
      Map<String, EventHandler> handlers = loadHandlersFromJars();
      handlers.forEach(builder::handler);
      logger.info("Registered {} handler(s) from JARs", handlers.size());
    } catch (IOException e) {
      logger.error("Failed to load handlers from JARs", e);
      throw new IllegalStateException("Handler loading failed", e);
    }

    return builder.build();
  }

  /**
   * Loads and instantiates handlers from configured JAR files.
   *
   * @return map of capability names to instantiated handlers
   * @throws IOException if JAR files cannot be read
   */
  private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
    HandlerLoader loader = HandlerLoader.jarLoader();
    return loader.loadHandlers(handlerJarPaths);
  }
}
