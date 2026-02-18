package io.eventbob.spring;

import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLoader;
import io.eventbob.spring.adapter.RemoteCapability;
import io.eventbob.spring.handlers.HealthcheckHandler;
import io.eventbob.spring.loader.RemoteHandlerLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring configuration for EventBob and its handlers.
 * <p>
 * This configuration:
 * </p>
 * <ul>
 *   <li>Loads local handlers from JAR files using JarHandlerLoader</li>
 *   <li>Loads remote handlers from configured endpoints using RemoteHandlerLoader</li>
 *   <li>Registers all handlers with EventBob (both local and remote)</li>
 *   <li>Keeps the healthcheck handler hard-coded for system health monitoring</li>
 * </ul>
 * <p>
 * This is a generic library configuration. Concrete applications must provide:
 * </p>
 * <ul>
 *   <li>{@code List<Path> handlerJarPaths} bean (local JAR-based handlers)</li>
 *   <li>{@code List<RemoteCapability> remoteCapabilities} bean (optional, remote handlers)</li>
 * </ul>
 */
@Configuration
public class EventBobConfig {
  private static final Logger logger = LoggerFactory.getLogger(EventBobConfig.class);

  private final List<Path> handlerJarPaths;
  private final List<RemoteCapability> remoteCapabilities;

  /**
   * Create EventBob configuration with specified handler sources.
   *
   * @param handlerJarPaths list of paths to handler JAR files to load
   * @param remoteCapabilities list of remote capability endpoints (optional, autowired if present)
   */
  public EventBobConfig(
          List<Path> handlerJarPaths,
          @Autowired(required = false) List<RemoteCapability> remoteCapabilities) {
    this.handlerJarPaths = handlerJarPaths;
    this.remoteCapabilities = remoteCapabilities != null ? remoteCapabilities : List.of();
  }

  /**
   * Create the HttpClient bean for remote handler communication.
   * <p>
   * This client is used by RemoteHandlerLoader to make HTTP calls to
   * remote capability endpoints.
   * </p>
   *
   * @return configured HTTP client instance
   */
  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
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
   *   <li>Loads and instantiates local handlers from JAR files</li>
   *   <li>Loads remote handlers from configured endpoints</li>
   *   <li>Merges both handler sets (checking for duplicates)</li>
   *   <li>Registers all handlers with EventBob</li>
   *   <li>Includes the healthcheck handler</li>
   * </ol>
   *
   * @param healthcheckHandler the healthcheck handler bean
   * @param httpClient the HTTP client for remote handlers
   * @return configured EventBob instance ready to process events
   */
  @Bean
  public EventBob eventBob(HealthcheckHandler healthcheckHandler, HttpClient httpClient) {
    EventBob.Builder builder = EventBob.builder();

    // Register hard-coded healthcheck handler
    builder.handler("healthcheck", healthcheckHandler);

    // Load and register all handlers (JAR-based and remote)
    try {
      Map<String, EventHandler> allHandlers = loadAllHandlers(httpClient);
      allHandlers.forEach(builder::handler);
      logger.info("Registered {} total handler(s)", allHandlers.size());
    } catch (IOException e) {
      logger.error("Failed to load handlers", e);
      throw new IllegalStateException("Handler loading failed", e);
    }

    return builder.build();
  }

  /**
   * Loads and merges handlers from all sources (JARs and remote endpoints).
   *
   * @param httpClient the HTTP client for remote handlers
   * @return map of capability names to instantiated handlers
   * @throws IOException if handler loading fails
   * @throws IllegalStateException if duplicate capabilities are found across sources
   */
  private Map<String, EventHandler> loadAllHandlers(HttpClient httpClient) throws IOException {
    Map<String, EventHandler> allHandlers = new HashMap<>();

    // Load JAR-based handlers
    HandlerLoader jarLoader = HandlerLoader.jarLoader(handlerJarPaths);
    Map<String, EventHandler> jarHandlers = jarLoader.loadHandlers();
    logger.info("Loaded {} handler(s) from JARs", jarHandlers.size());
    allHandlers.putAll(jarHandlers);

    // Load remote handlers
    HandlerLoader remoteLoader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> remoteHandlers = remoteLoader.loadHandlers();
    logger.info("Loaded {} remote handler(s)", remoteHandlers.size());

    // Check for duplicates before merging
    for (String capability : remoteHandlers.keySet()) {
      if (allHandlers.containsKey(capability)) {
        throw new IllegalStateException(
                "Duplicate capability '" + capability + "' found in both JAR and remote handlers");
      }
    }

    allHandlers.putAll(remoteHandlers);
    return allHandlers;
  }
}
