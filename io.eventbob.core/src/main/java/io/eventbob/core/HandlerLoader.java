package io.eventbob.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Loads and instantiates EventHandler implementations from various sources.
 * <p>
 * Implementations can load handlers from:
 * </p>
 * <ul>
 *   <li>JAR files with lifecycle support (see {@link #jarLoader(Collection)})</li>
 *   <li>Remote endpoints via HTTP/gRPC/etc (adapter pattern)</li>
 *   <li>Any other source that provides EventHandler implementations</li>
 * </ul>
 * <p>
 * Implementations receive all dependencies via constructor. The parameterless
 * {@link #loadHandlers()} method uses those constructor-provided dependencies
 * to discover and instantiate handlers.
 * </p>
 * <p>
 * <b>Resource Management:</b>
 * </p>
 * <p>
 * HandlerLoader extends {@link AutoCloseable} to support resource cleanup. Implementations
 * that manage resources (class loaders, handler lifecycles, HTTP clients) should release
 * them in {@link #close()}. This ensures proper cleanup when EventBob shuts down.
 * </p>
 */
public interface HandlerLoader extends AutoCloseable {
    /**
     * Loads and instantiates handlers using dependencies provided via constructor.
     * <p>
     * Implementations use constructor-injected configuration (JAR paths, remote endpoints,
     * etc.) to discover and instantiate handlers. Each discovered handler is returned
     * with its capability name.
     * </p>
     *
     * @return map of capability names to instantiated handler instances
     * @throws IOException if handler loading fails due to I/O errors
     * @throws IllegalStateException if duplicate capability names are found or instantiation fails
     */
    Map<String, EventHandler> loadHandlers() throws IOException;

    /**
     * Releases resources held by this loader.
     * <p>
     * Implementations should release class loaders, shutdown handler lifecycles,
     * close HTTP clients, and perform any other cleanup necessary. This method
     * is called when EventBob shuts down.
     * </p>
     * <p>
     * Implementations that don't hold resources can provide an empty implementation.
     * </p>
     *
     * @throws Exception if cleanup fails (logged but does not prevent shutdown)
     */
    void close() throws Exception;

    /**
     * Creates a JAR-based handler loader for POJO handlers.
     * <p>
     * This factory method returns an implementation that loads handlers
     * from JAR files using isolated class loaders. Each JAR is scanned for
     * classes that implement {@link EventHandler} and are annotated with
     * {@link Capability}.
     * </p>
     * <p>
     * <b>Use this for simple handlers with no-arg constructors and no dependencies.</b>
     * </p>
     * <p>
     * For handlers that need initialization (Spring, Dropwizard, databases, etc.),
     * use {@link #lifecycleLoader(Collection, Dispatcher)} instead.
     * </p>
     *
     * @param jarPaths collection of paths to JAR files to scan
     * @return a new handler loader instance configured with the specified JAR paths
     */
    static HandlerLoader jarLoader(Collection<Path> jarPaths) {
        return new JarHandlerLoader(jarPaths);
    }

    /**
     * Creates a lifecycle-based handler loader for full microservice handlers.
     * <p>
     * This factory method returns an implementation that loads handlers using the
     * {@link HandlerLifecycle} contract. Handler JARs provide a lifecycle implementation
     * that knows how to initialize itself with Spring, Dropwizard, manual wiring, etc.
     * </p>
     * <p>
     * <b>Use this for handlers that need:</b>
     * </p>
     * <ul>
     *   <li>Configuration (database URLs, API keys, etc.)</li>
     *   <li>Dependencies (DataSource, HTTP clients, etc.)</li>
     *   <li>Framework integration (Spring contexts, Dropwizard environments)</li>
     *   <li>Startup/shutdown hooks (resource management)</li>
     * </ul>
     *
     * @param jarPaths collection of paths to JAR files to load
     * @param dispatcher the dispatcher to provide to handlers
     * @return a new lifecycle handler loader instance
     */
    static HandlerLoader lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher) {
        return new LifecycleHandlerLoader(jarPaths, dispatcher);
    }

    /**
     * Creates a lifecycle-based handler loader with framework context.
     * <p>
     * Similar to {@link #lifecycleLoader(Collection, Dispatcher)} but also provides
     * framework-specific context (Spring ApplicationContext, Dropwizard Environment, etc.)
     * that handlers can optionally use.
     * </p>
     *
     * @param jarPaths collection of paths to JAR files to load
     * @param dispatcher the dispatcher to provide to handlers
     * @param frameworkContext optional framework-specific context (e.g., ApplicationContext)
     * @return a new lifecycle handler loader instance with framework context
     */
    static HandlerLoader lifecycleLoader(Collection<Path> jarPaths, Dispatcher dispatcher, Object frameworkContext) {
        return new LifecycleHandlerLoader(jarPaths, dispatcher, frameworkContext);
    }
}
