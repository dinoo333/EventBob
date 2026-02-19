package io.eventbob.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads EventHandler implementations from JAR files using the HandlerLifecycle contract.
 * <p>
 * This loader supports "full microservice" handlers that need initialization (Spring contexts,
 * Dropwizard environments, database connections, etc.). Handler JARs provide a lifecycle
 * implementation that knows how to wire itself using the chosen framework.
 * </p>
 * <p>
 * <b>JAR Structure:</b>
 * </p>
 * <pre>
 * handler.jar
 * ├── com/example/MyHandlerLifecycle.class
 * ├── com/example/MyHandler.class (implements EventHandler)
 * ├── META-INF/
 * │   └── eventbob-handler.properties (declares lifecycle.class)
 * └── application.yml (handler-specific configuration)
 * </pre>
 * <p>
 * <b>eventbob-handler.properties format:</b>
 * </p>
 * <pre>
 * lifecycle.class=com.example.MyHandlerLifecycle
 * </pre>
 * <p>
 * <b>Lifecycle Process:</b>
 * </p>
 * <ol>
 *   <li>Load JAR into isolated URLClassLoader</li>
 *   <li>Read META-INF/eventbob-handler.properties to find lifecycle class</li>
 *   <li>Load application.yml configuration (not yet implemented - empty map provided)</li>
 *   <li>Instantiate lifecycle class</li>
 *   <li>Call lifecycle.initialize(context) with configuration and dispatcher</li>
 *   <li>Call lifecycle.getHandler() to retrieve initialized handler</li>
 *   <li>Extract capabilities from handler's @Capability annotations</li>
 *   <li>Track lifecycle for shutdown</li>
 * </ol>
 * <p>
 * <b>Error Handling:</b>
 * </p>
 * <ul>
 *   <li>Missing eventbob-handler.properties: JAR is skipped (logged as warning)</li>
 *   <li>Initialization failure: Handler not registered, other handlers continue (graceful degradation)</li>
 *   <li>Duplicate capabilities: throws IllegalStateException</li>
 * </ul>
 * <p>
 * <b>Shutdown:</b>
 * </p>
 * <p>
 * When {@link #close()} is called, all tracked lifecycles are shut down via
 * {@link HandlerLifecycle#shutdown()}, followed by closing all URLClassLoaders.
 * This ensures resources (database connections, Spring contexts, thread pools,
 * class loader resources) are released cleanly. Class loaders are closed after
 * lifecycle shutdown to allow handlers to reference classes during shutdown.
 * </p>
 *
 * @see HandlerLifecycle
 * @see LifecycleContext
 */
class LifecycleHandlerLoader implements HandlerLoader {
    private static final Logger logger = Logger.getLogger(LifecycleHandlerLoader.class.getName());
    private static final String HANDLER_PROPERTIES = "META-INF/eventbob-handler.properties";
    private static final String LIFECYCLE_CLASS_KEY = "lifecycle.class";
    private static final String CONFIG_FILE = "application.yml";

    private final Collection<Path> jarPaths;
    private final Dispatcher dispatcher;
    private final Object frameworkContext;
    private final List<HandlerLifecycle> lifecycles = new ArrayList<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    /**
     * Creates a lifecycle handler loader with specified JAR paths and dispatcher.
     *
     * @param jarPaths collection of paths to JAR files to load
     * @param dispatcher the dispatcher to provide to handlers
     */
    LifecycleHandlerLoader(Collection<Path> jarPaths, Dispatcher dispatcher) {
        this(jarPaths, dispatcher, null);
    }

    /**
     * Creates a lifecycle handler loader with JAR paths, dispatcher, and framework context.
     *
     * @param jarPaths collection of paths to JAR files to load
     * @param dispatcher the dispatcher to provide to handlers
     * @param frameworkContext optional framework-specific context (Spring ApplicationContext, etc.)
     */
    LifecycleHandlerLoader(Collection<Path> jarPaths, Dispatcher dispatcher, Object frameworkContext) {
        this.jarPaths = jarPaths;
        this.dispatcher = dispatcher;
        this.frameworkContext = frameworkContext;
    }

    @Override
    public Map<String, EventHandler> loadHandlers() throws IOException {
        Map<String, EventHandler> handlers = new HashMap<>();

        for (Path jarPath : jarPaths) {
            try {
                loadHandlerFromJar(jarPath, handlers);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load handler from " + jarPath + ": " + e.getMessage(), e);
                // Continue with other JARs (graceful degradation)
            }
        }

        return handlers;
    }

    /**
     * Loads a handler from a single JAR file.
     *
     * @param jarPath path to the JAR file
     * @param handlers map to populate with loaded handlers
     * @throws Exception if loading fails
     */
    private void loadHandlerFromJar(Path jarPath, Map<String, EventHandler> handlers) throws Exception {
        if (!Files.exists(jarPath)) {
            throw new IOException("JAR file not found: " + jarPath);
        }

        // Create isolated class loader for this JAR
        URL jarUrl = jarPath.toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{jarUrl},
            HandlerLifecycle.class.getClassLoader()
        );
        classLoaders.add(classLoader); // Track for cleanup

        // Check for lifecycle properties file
        String lifecycleClassName = findLifecycleClass(classLoader);
        if (lifecycleClassName == null) {
            logger.log(Level.WARNING, "JAR " + jarPath + " does not contain " + HANDLER_PROPERTIES + ", skipping");
            return;
        }

        // Load configuration
        Map<String, Object> config = loadConfiguration(classLoader);

        // Instantiate and initialize lifecycle
        HandlerLifecycle lifecycle = instantiateLifecycle(classLoader, lifecycleClassName);
        LifecycleContext context = new LifecycleContextImpl(config, dispatcher, frameworkContext);

        try {
            lifecycle.initialize(context);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to initialize handler from " + jarPath + " (lifecycle: " + lifecycleClassName + ")", e);
        }

        // Get handler and extract capabilities
        EventHandler handler = lifecycle.getHandler();
        if (handler == null) {
            throw new IllegalStateException(
                "Lifecycle " + lifecycleClassName + " returned null handler");
        }

        // Extract capabilities from handler's @Capability annotations
        List<String> capabilities = extractCapabilities(handler.getClass());
        if (capabilities.isEmpty()) {
            throw new IllegalStateException(
                "Handler " + handler.getClass().getName() + " has no @Capability annotations");
        }

        // Register handler for each capability
        for (String capability : capabilities) {
            if (handlers.containsKey(capability)) {
                throw new IllegalStateException("Duplicate capability: " + capability);
            }
            handlers.put(capability, handler);
        }

        // Track lifecycle for shutdown
        lifecycles.add(lifecycle);

        logger.log(Level.INFO, "Loaded handler from " + jarPath + " with capabilities: " + capabilities);
    }

    /**
     * Finds the lifecycle class name from META-INF/eventbob-handler.properties.
     *
     * @param classLoader the class loader for the JAR
     * @return lifecycle class name, or null if not found
     */
    private String findLifecycleClass(ClassLoader classLoader) {
        try (InputStream is = classLoader.getResourceAsStream(HANDLER_PROPERTIES)) {
            if (is == null) {
                return null;
            }

            Properties props = new Properties();
            props.load(is);
            return props.getProperty(LIFECYCLE_CLASS_KEY);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read " + HANDLER_PROPERTIES, e);
            return null;
        }
    }

    /**
     * Loads configuration from application.yml (if present).
     * <p>
     * <b>Note:</b> Configuration loading from application.yml is not yet implemented.
     * Currently returns an empty map. Future versions will support YAML configuration
     * parsing using SnakeYAML or similar library.
     * </p>
     *
     * @param classLoader the class loader for the JAR
     * @return configuration map (currently always empty, pending YAML parsing implementation)
     */
    private Map<String, Object> loadConfiguration(ClassLoader classLoader) {
        // TODO: Implement YAML parsing using SnakeYAML or similar
        // For now, return empty map - handlers must not depend on configuration
        return Map.of();
    }

    /**
     * Instantiates the lifecycle class.
     *
     * @param classLoader the class loader for the JAR
     * @param lifecycleClassName the fully-qualified lifecycle class name
     * @return instantiated lifecycle
     * @throws Exception if instantiation fails
     */
    private HandlerLifecycle instantiateLifecycle(ClassLoader classLoader, String lifecycleClassName) throws Exception {
        Class<?> lifecycleClass = classLoader.loadClass(lifecycleClassName);

        if (!HandlerLifecycle.class.isAssignableFrom(lifecycleClass)) {
            throw new IllegalStateException(
                "Lifecycle class " + lifecycleClassName + " does not extend HandlerLifecycle");
        }

        return (HandlerLifecycle) lifecycleClass.getDeclaredConstructor().newInstance();
    }

    /**
     * Extracts capability names from handler's @Capability annotations.
     *
     * @param handlerClass the handler class
     * @return list of capability names
     */
    private List<String> extractCapabilities(Class<?> handlerClass) {
        Capability[] annotations = handlerClass.getAnnotationsByType(Capability.class);
        List<String> capabilities = new ArrayList<>(annotations.length);
        for (Capability annotation : annotations) {
            capabilities.add(annotation.value());
        }
        return capabilities;
    }

    @Override
    public void close() throws Exception {
        List<Exception> failures = new ArrayList<>();

        // Shut down lifecycles first (handlers may reference classes during shutdown)
        for (HandlerLifecycle lifecycle : lifecycles) {
            try {
                lifecycle.shutdown();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to shutdown lifecycle: " + e.getMessage(), e);
                failures.add(e);
            }
        }

        // Close class loaders after lifecycles are shut down
        for (URLClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to close class loader: " + e.getMessage(), e);
                failures.add(e);
            }
        }

        // If any shutdowns failed, throw the first exception
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
    }
}
