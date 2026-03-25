package io.eventbob.dropwizard;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import io.eventbob.core.Capability;
import io.eventbob.core.EventBob;
import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.HandlerLoader;
import io.eventbob.core.LifecycleContext;
import io.eventbob.dropwizard.adapter.RemoteCapability;
import io.eventbob.dropwizard.handlers.HealthcheckHandler;
import io.eventbob.dropwizard.loader.RemoteHandlerLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dropwizard Bundle for EventBob and its handlers.
 * <p>
 * This bundle supports three handler registration paths:
 * </p>
 * <ul>
 *   <li><b>Inline lifecycles</b> — {@code HandlerLifecycle} instances passed directly;
 *       initialized without classloader isolation. Use this when the lifecycle class
 *       lives in the same JVM as the microlith.</li>
 *   <li><b>JAR-based lifecycles</b> — handler JARs loaded via {@code handlerJarPaths};
 *       each JAR is loaded in an isolated {@code URLClassLoader}. Use this when
 *       shipping handler JARs as separate deployment artifacts.</li>
 *   <li><b>Remote handlers</b> — capabilities delegated to remote microliths via HTTP.</li>
 * </ul>
 * <p>
 * All three paths are optional. Concrete applications provide whichever apply.
 * </p>
 */
public class EventBobBundle implements ConfiguredBundle<Configuration> {
    private static final Logger logger = LoggerFactory.getLogger(EventBobBundle.class);

    private final List<Path> handlerJarPaths;
    private final List<HandlerLifecycle> inlineLifecycles;
    private final List<RemoteCapability> remoteCapabilities;

    /**
     * Create EventBob bundle with specified handler sources.
     * <p>
     * All parameters are optional. Pass {@code null} for any source that does not
     * apply to the current deployment.
     * </p>
     *
     * @param handlerJarPaths    paths to handler JAR files to load (optional)
     * @param inlineLifecycles   lifecycle instances to initialize directly (optional)
     * @param remoteCapabilities remote capability endpoints (optional)
     */
    public EventBobBundle(List<Path> handlerJarPaths, List<HandlerLifecycle> inlineLifecycles,
                          List<RemoteCapability> remoteCapabilities) {
        this.handlerJarPaths = handlerJarPaths != null ? handlerJarPaths : List.of();
        this.inlineLifecycles = inlineLifecycles != null ? inlineLifecycles : List.of();
        this.remoteCapabilities = remoteCapabilities != null ? remoteCapabilities : List.of();
    }

    /**
     * Create the HttpClient for remote handler communication.
     *
     * @return configured HTTP client instance
     */
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Create the healthcheck handler.
     *
     * @return the healthcheck handler instance
     */
    public HealthcheckHandler healthcheckHandler() {
        return new HealthcheckHandler();
    }

    /**
     * Create the EventBob instance with all registered handlers.
     * <p>
     * Loads handlers from all configured sources (inline lifecycles, JARs, remote),
     * checks for duplicate capabilities, and registers everything with EventBob.
     * </p>
     *
     * @param healthcheckHandler the healthcheck handler
     * @param httpClient         the HTTP client for remote handlers
     * @return configured EventBob instance ready to process events
     * @throws Exception if handler loading fails
     */
    public EventBob eventBob(HealthcheckHandler healthcheckHandler, HttpClient httpClient) throws Exception {
        EventBob.Builder builder = EventBob.builder();

        builder.handler("healthcheck", healthcheckHandler);

        Map<String, EventHandler> allHandlers = loadAllHandlers(httpClient);
        allHandlers.forEach(builder::handler);
        logger.info("Registered {} total handler(s)", allHandlers.size());

        return builder.build();
    }

    /**
     * Shuts down all inline lifecycle handlers.
     */
    public void shutdownInlineLifecycles() {
        for (HandlerLifecycle lifecycle : inlineLifecycles) {
            try {
                lifecycle.shutdown();
            } catch (Exception e) {
                logger.warn("Error shutting down inline lifecycle {}", lifecycle.getClass().getSimpleName(), e);
            }
        }
    }

    private Map<String, EventHandler> loadAllHandlers(HttpClient httpClient) throws Exception {
        Map<String, EventHandler> allHandlers = new HashMap<>();

        // Register inline lifecycle-based handlers (same JVM, no classloader isolation)
        // Dispatcher is intentionally null: handlers receive it at event-processing time
        // via EventHandler.handle(Event, Dispatcher).
        if (!inlineLifecycles.isEmpty()) {
            LifecycleContext lifecycleContext = LifecycleContext.of(Map.of(), null);
            int registeredCapabilities = 0;
            for (HandlerLifecycle lifecycle : inlineLifecycles) {
                lifecycle.initialize(lifecycleContext);
                EventHandler handler = lifecycle.getHandler();
                Capability[] capabilities = handler.getClass().getAnnotationsByType(Capability.class);
                if (capabilities.length == 0) {
                    logger.warn("Inline lifecycle {} returned a handler with no @Capability annotations; skipping",
                            lifecycle.getClass().getSimpleName());
                    continue;
                }
                for (Capability capability : capabilities) {
                    if (allHandlers.containsKey(capability.value())) {
                        throw new IllegalStateException(
                                "Duplicate capability '" + capability.value() + "' from inline lifecycle "
                                + lifecycle.getClass().getSimpleName());
                    }
                    allHandlers.put(capability.value(), handler);
                    registeredCapabilities++;
                }
            }
            logger.info("Registered {} capability(-ies) from {} inline lifecycle(s)",
                    registeredCapabilities, inlineLifecycles.size());
        }

        // Load lifecycle-based handlers from JARs (isolated URLClassLoader per JAR)
        if (!handlerJarPaths.isEmpty()) {
            HandlerLoader lifecycleLoader = HandlerLoader.lifecycleLoader(handlerJarPaths, null);
            Map<String, EventHandler> jarHandlers = lifecycleLoader.loadHandlers();
            for (Map.Entry<String, EventHandler> entry : jarHandlers.entrySet()) {
                if (allHandlers.containsKey(entry.getKey())) {
                    throw new IllegalStateException(
                            "Duplicate capability '" + entry.getKey() + "' found in JAR handlers and inline lifecycles");
                }
            }
            allHandlers.putAll(jarHandlers);
            logger.info("Loaded {} capability(-ies) from JARs", jarHandlers.size());
        }

        // Load remote handlers
        HandlerLoader remoteLoader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
        Map<String, EventHandler> remoteHandlers = remoteLoader.loadHandlers();
        for (String capability : remoteHandlers.keySet()) {
            if (allHandlers.containsKey(capability)) {
                throw new IllegalStateException(
                        "Duplicate capability '" + capability + "' found in remote handlers and local handlers");
            }
        }
        allHandlers.putAll(remoteHandlers);
        logger.info("Loaded {} remote capability(-ies)", remoteHandlers.size());

        return allHandlers;
    }

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // No bootstrap-time initialization required
    }

    @Override
    public void run(Configuration configuration, Environment environment) throws Exception {
        HttpClient client = httpClient();
        HealthcheckHandler hc = healthcheckHandler();
        EventBob eb = eventBob(hc, client);
        environment.jersey().register(new EventResource(eb));
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() {}

            @Override
            public void stop() {
                shutdownInlineLifecycles();
                eb.close();
            }
        });
    }
}
