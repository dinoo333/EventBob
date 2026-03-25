package io.eventbob.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

/**
 * Loads EventHandler implementations from JAR files using isolated class loaders.
 * <p>
 * This implementation creates a separate URLClassLoader for each JAR file, with the
 * parent set to the class loader that loaded EventHandler. This ensures:
 * </p>
 * <ul>
 *   <li>Each JAR is isolated from others</li>
 *   <li>Core EventBob classes are shared across all handlers</li>
 *   <li>Handlers can be loaded without polluting the main classpath</li>
 * </ul>
 * <p>
 * Handlers may declare multiple capabilities using {@link Capabilities @Capabilities}
 * (or multiple {@link Capability @Capability} annotations via Java's repeatable
 * annotation mechanism). When a handler declares multiple capabilities, a single
 * instance is created and registered under each capability name.
 * </p>
 * <p>
 * Error handling:
 * </p>
 * <ul>
 *   <li>Missing JAR files: throws IOException</li>
 *   <li>Malformed JAR files: logged as warning, JAR is skipped</li>
 *   <li>Duplicate capabilities: throws IllegalStateException</li>
 *   <li>Class loading failures: logged at debug level, class is skipped</li>
 *   <li>Instantiation failures: throws IllegalStateException</li>
 * </ul>
 */
class JarHandlerLoader implements HandlerLoader {
    private static final Logger logger = Logger.getLogger(JarHandlerLoader.class.getName());
    private static final String CLASS_SUFFIX = ".class";

    private final Collection<Path> jarPaths;

    /**
     * Creates a JAR handler loader with specified JAR paths.
     *
     * @param jarPaths collection of paths to JAR files to scan
     */
    JarHandlerLoader(Collection<Path> jarPaths) {
        this.jarPaths = jarPaths;
    }

    @Override
    public Map<String, EventHandler> loadHandlers() throws IOException {
        List<DiscoveredHandler> discoveredHandlers = discoverHandlers(jarPaths);
        return instantiateHandlers(discoveredHandlers);
    }

    @Override
    public void close() {
        // POJO handlers have no resources to clean up
        // Class loaders are not explicitly closed (garbage collected when no longer referenced)
    }

    /**
     * Discovers handlers from JAR files without instantiating them.
     * <p>
     * This is an internal method that performs the discovery phase, returning
     * metadata about discovered handlers.
     * </p>
     *
     * @param jarPaths collection of paths to JAR files to scan
     * @return list of discovered handler metadata
     * @throws IOException if a JAR file cannot be read
     */
    private List<DiscoveredHandler> discoverHandlers(Collection<Path> jarPaths) throws IOException {
        List<DiscoveredHandler> handlers = new ArrayList<>();
        Set<String> seenCapabilities = new HashSet<>();

        for (Path jarPath : jarPaths) {
            validateJarExists(jarPath);
            processJar(jarPath, handlers, seenCapabilities);
        }

        return handlers;
    }

    /**
     * Instantiates all discovered handlers.
     * <p>
     * When a handler class declares multiple capabilities, a single instance is created
     * and shared across all its capability registrations. The instance cache ensures
     * each handler class is instantiated exactly once.
     * </p>
     *
     * @param discoveredHandlers list of discovered handler metadata
     * @return map of capability names to instantiated handlers
     * @throws IllegalStateException if any handler fails to instantiate
     */
    private Map<String, EventHandler> instantiateHandlers(List<DiscoveredHandler> discoveredHandlers) {
        Map<String, EventHandler> handlers = new HashMap<>();
        Map<Class<? extends EventHandler>, EventHandler> instanceCache = new HashMap<>();

        for (DiscoveredHandler discovered : discoveredHandlers) {
            EventHandler handler = instanceCache.computeIfAbsent(
                discovered.handlerClass(), this::instantiateHandler);
            handlers.put(discovered.capability(), handler);
        }

        return handlers;
    }

    /**
     * Instantiates a handler class using its no-args constructor.
     *
     * @param handlerClass the handler class to instantiate
     * @return the instantiated handler
     * @throws IllegalStateException if instantiation fails
     */
    private EventHandler instantiateHandler(Class<? extends EventHandler> handlerClass) {
        try {
            return handlerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate handler: " + handlerClass.getName(), e);
        }
    }

    /**
     * Validates that a JAR file exists at the specified path.
     *
     * @param jarPath the path to validate
     * @throws IOException if the file does not exist or is not readable
     */
    private void validateJarExists(Path jarPath) throws IOException {
        if (!Files.exists(jarPath)) {
            throw new IOException("JAR file not found: " + jarPath);
        }
        if (!Files.isReadable(jarPath)) {
            throw new IOException("JAR file not readable: " + jarPath);
        }
    }

    /**
     * Processes a single JAR file, discovering all handlers within it.
     *
     * @param jarPath the path to the JAR file
     * @param handlers the list to accumulate discovered handlers
     * @param seenCapabilities the set of capabilities already seen (for duplicate detection)
     * @throws IOException if the JAR cannot be read
     */
    private void processJar(Path jarPath, List<DiscoveredHandler> handlers, Set<String> seenCapabilities) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile());
             URLClassLoader classLoader = createClassLoader(jarPath)) {
            scanJarForHandlers(jarFile, classLoader, handlers, seenCapabilities);
        } catch (ZipException e) {
            logger.log(Level.WARNING, "Malformed JAR file, skipping: " + jarPath, e);
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Creates an isolated URLClassLoader for a JAR file.
     * <p>
     * The parent class loader is set to the class loader that loaded EventHandler,
     * ensuring core EventBob classes are shared while the JAR remains isolated.
     * </p>
     *
     * @param jarPath the path to the JAR file
     * @return a new URLClassLoader configured for this JAR
     * @throws IOException if the JAR URL cannot be constructed
     */
    private URLClassLoader createClassLoader(Path jarPath) throws IOException {
        URL jarUrl = jarPath.toUri().toURL();
        ClassLoader parentClassLoader = EventHandler.class.getClassLoader();
        return new URLClassLoader(new URL[]{jarUrl}, parentClassLoader);
    }

    /**
     * Scans a JAR file for EventHandler implementations with @Capability annotations.
     *
     * @param jarFile the JAR file to scan
     * @param classLoader the class loader to use for loading classes
     * @param handlers the list to accumulate discovered handlers
     * @param seenCapabilities the set of capabilities already seen
     */
    private void scanJarForHandlers(JarFile jarFile, URLClassLoader classLoader,
                                   List<DiscoveredHandler> handlers, Set<String> seenCapabilities) {
        jarFile.stream()
            .filter(this::isClassFile)
            .forEach(entry -> processClassEntry(entry, classLoader, handlers, seenCapabilities));
    }

    /**
     * Checks if a JAR entry represents a class file.
     *
     * @param entry the JAR entry to check
     * @return true if the entry is a .class file, false otherwise
     */
    private boolean isClassFile(JarEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(CLASS_SUFFIX);
    }

    /**
     * Processes a single class file entry from a JAR.
     *
     * @param entry the JAR entry representing a class file
     * @param classLoader the class loader to use for loading the class
     * @param handlers the list to accumulate discovered handlers
     * @param seenCapabilities the set of capabilities already seen
     */
    private void processClassEntry(JarEntry entry, URLClassLoader classLoader,
                                   List<DiscoveredHandler> handlers, Set<String> seenCapabilities) {
        String className = entryNameToClassName(entry.getName());

        try {
            Class<?> clazz = classLoader.loadClass(className);
            processLoadedClass(clazz, handlers, seenCapabilities);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            logger.log(Level.FINE, "Failed to load class: " + className, e);
        }
    }

    /**
     * Converts a JAR entry name to a fully qualified class name.
     * <p>
     * Example: "com/example/Handler.class" becomes "com.example.Handler"
     * </p>
     *
     * @param entryName the JAR entry name
     * @return the fully qualified class name
     */
    private String entryNameToClassName(String entryName) {
        String withoutSuffix = entryName.substring(0, entryName.length() - CLASS_SUFFIX.length());
        return withoutSuffix.replace('/', '.');
    }

    /**
     * Processes a loaded class, checking if it is a valid handler.
     *
     * @param clazz the loaded class
     * @param handlers the list to accumulate discovered handlers
     * @param seenCapabilities the set of capabilities already seen
     */
    private void processLoadedClass(Class<?> clazz, List<DiscoveredHandler> handlers, Set<String> seenCapabilities) {
        if (isValidHandler(clazz)) {
            Class<? extends EventHandler> handlerClass = clazz.asSubclass(EventHandler.class);
            List<String> capabilities = extractCapabilities(handlerClass);

            for (String capability : capabilities) {
                checkForDuplicateCapability(capability, seenCapabilities);
                handlers.add(new DiscoveredHandler(capability, handlerClass));
                seenCapabilities.add(capability);
            }
        }
    }

    /**
     * Checks if a class is a valid EventHandler implementation.
     * <p>
     * A valid handler must:
     * </p>
     * <ul>
     *   <li>Implement EventHandler interface</li>
     *   <li>Be annotated with {@link Capability} or {@link Capabilities}</li>
     * </ul>
     *
     * @param clazz the class to check
     * @return true if the class is a valid handler, false otherwise
     */
    private boolean isValidHandler(Class<?> clazz) {
        return EventHandler.class.isAssignableFrom(clazz) &&
               (clazz.isAnnotationPresent(Capability.class) || clazz.isAnnotationPresent(Capabilities.class));
    }

    /**
     * Extracts all capability names from a handler class's {@link Capability} annotations.
     * <p>
     * Uses {@code getAnnotationsByType} which handles both a single {@link Capability}
     * annotation and the {@link Capabilities} container annotation uniformly.
     * </p>
     *
     * @param handlerClass the handler class
     * @return list of capability names declared on this handler
     */
    private List<String> extractCapabilities(Class<? extends EventHandler> handlerClass) {
        Capability[] annotations = handlerClass.getAnnotationsByType(Capability.class);
        List<String> capabilities = new ArrayList<>(annotations.length);
        for (Capability annotation : annotations) {
            capabilities.add(annotation.value());
        }
        return capabilities;
    }

    /**
     * Checks for duplicate capability names.
     *
     * @param capability the capability name to check
     * @param seenCapabilities the set of capabilities already seen
     * @throws IllegalStateException if the capability has already been registered
     */
    private void checkForDuplicateCapability(String capability, Set<String> seenCapabilities) {
        if (seenCapabilities.contains(capability)) {
            throw new IllegalStateException("Duplicate capability found: " + capability);
        }
    }
}
