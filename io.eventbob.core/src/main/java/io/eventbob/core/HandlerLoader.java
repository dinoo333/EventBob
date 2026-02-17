package io.eventbob.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Loads and instantiates EventHandler implementations from JAR files.
 * <p>
 * Implementations scan provided JAR files for classes that:
 * </p>
 * <ul>
 *   <li>Implement {@link io.eventbob.core.EventHandler}</li>
 *   <li>Are annotated with {@link io.eventbob.core.Capability}</li>
 * </ul>
 * <p>
 * Each discovered handler is instantiated and returned with its capability name
 * extracted from the annotation.
 * </p>
 */
public interface HandlerLoader {
    /**
     * Loads and instantiates handlers from the specified JAR files.
     * <p>
     * Each JAR is loaded in its own isolated class loader. Classes are scanned
     * for EventHandler implementations with @Capability annotations, then instantiated
     * using their no-args constructor.
     * </p>
     *
     * @param jarPaths collection of paths to JAR files to scan
     * @return map of capability names to instantiated handler instances
     * @throws IOException if a JAR file cannot be read
     * @throws IllegalStateException if duplicate capability names are found or instantiation fails
     */
    Map<String, EventHandler> loadHandlers(Collection<Path> jarPaths) throws IOException;

    /**
     * Creates a JAR-based handler loader.
     * <p>
     * This factory method returns the default implementation that loads handlers
     * from JAR files using isolated class loaders.
     * </p>
     *
     * @return a new handler loader instance
     */
    static HandlerLoader jarLoader() {
        return new JarHandlerLoader();
    }
}
