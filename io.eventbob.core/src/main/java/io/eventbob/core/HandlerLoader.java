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
 *   <li>JAR files (see {@link #jarLoader(Collection)})</li>
 *   <li>Remote endpoints via HTTP/gRPC/etc (adapter pattern)</li>
 *   <li>Any other source that provides EventHandler implementations</li>
 * </ul>
 * <p>
 * Implementations receive all dependencies via constructor. The parameterless
 * {@link #loadHandlers()} method uses those constructor-provided dependencies
 * to discover and instantiate handlers.
 * </p>
 */
public interface HandlerLoader {
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
     * Creates a JAR-based handler loader.
     * <p>
     * This factory method returns an implementation that loads handlers
     * from JAR files using isolated class loaders. Each JAR is scanned for
     * classes that implement {@link EventHandler} and are annotated with
     * {@link Capability}.
     * </p>
     *
     * @param jarPaths collection of paths to JAR files to scan
     * @return a new handler loader instance configured with the specified JAR paths
     */
    static HandlerLoader jarLoader(Collection<Path> jarPaths) {
        return new JarHandlerLoader(jarPaths);
    }
}
