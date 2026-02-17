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
import java.nio.file.Paths;
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
 */
@Configuration
public class EventBobConfig {
  private static final Logger logger = LoggerFactory.getLogger(EventBobConfig.class);

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
   * <p>
   * JAR paths are currently hard-coded to the example modules:
   * </p>
   * <ul>
   *   <li>io.eventbob.example.lower</li>
   *   <li>io.eventbob.example.echo</li>
   * </ul>
   *
   * @return map of capability names to instantiated handlers
   * @throws IOException if JAR files cannot be read
   */
  private Map<String, EventHandler> loadHandlersFromJars() throws IOException {
    HandlerLoader loader = HandlerLoader.jarLoader();

    List<Path> jarPaths = List.of(
        Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
        Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
    );

    return loader.loadHandlers(jarPaths);
  }
}
