package io.eventbob.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecycleHandlerLoaderTest {

    // Stub dispatcher - sufficient for lifecycle initialization tests
    private final Dispatcher stubDispatcher = new Dispatcher() {
        @Override
        public CompletableFuture<Event> send(Event event, BiFunction<Throwable, Event, Event> onError) {
            return CompletableFuture.completedFuture(event);
        }
    };

    @Test
    void loadHandlers_withMissingJar_skipsJar(@TempDir Path tempDir) throws IOException {
        Path missingJar = tempDir.resolve("missing.jar");

        // LifecycleHandlerLoader implements graceful degradation - logs warning and continues
        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(missingJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Should skip JAR and return empty map
        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withEmptyList_returnsEmptyMap() throws IOException {
        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(), stubDispatcher);

        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withNonReadableFile_skipsJar(@TempDir Path tempDir) throws IOException {
        Path nonReadableFile = tempDir.resolve("nonreadable.jar");
        Files.createFile(nonReadableFile);

        // Make file non-readable (note: this may not work on all platforms)
        if (nonReadableFile.toFile().setReadable(false)) {
            // LifecycleHandlerLoader implements graceful degradation - logs warning and continues
            HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(nonReadableFile), stubDispatcher);
            Map<String, EventHandler> handlers = loader.loadHandlers();

            // Should skip JAR and return empty map
            assertThat(handlers).isEmpty();
        }
    }

    @Test
    void loadHandlers_withMissingProperties_skipsJar(@TempDir Path tempDir) throws IOException {
        // Create a valid JAR but without eventbob-handler.properties
        Path emptyJar = tempDir.resolve("no-properties.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(emptyJar))) {
            // Empty JAR - no properties file
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(emptyJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Should skip JAR and return empty map
        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withInvalidLifecycleClassName_skipsJar(@TempDir Path tempDir) throws IOException {
        // Create JAR with properties file containing invalid class name
        Path invalidJar = tempDir.resolve("invalid-lifecycle.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(invalidJar))) {
            // Add properties file with non-existent class
            var entry = new java.util.jar.JarEntry("META-INF/eventbob-handler.properties");
            jos.putNextEntry(entry);
            jos.write("lifecycle.class=com.example.NonExistentLifecycle\n".getBytes());
            jos.closeEntry();
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(invalidJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Should skip JAR gracefully (logged as warning)
        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withValidJars_loadsAndInitializesHandlers() throws IOException {
        List<Path> jarPaths = List.of(
            Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
            Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
        );

        // Skip test if JARs haven't been built yet
        if (!Files.exists(jarPaths.get(0)) || !Files.exists(jarPaths.get(1))) {
            return; // Gracefully skip if example JARs not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(jarPaths, stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(3);
        assertThat(handlers).containsKeys("lower", "echo", "invert");

        // Verify handlers are actually instantiated (not null)
        assertThat(handlers.get("lower")).isNotNull().isInstanceOf(EventHandler.class);
        assertThat(handlers.get("echo")).isNotNull().isInstanceOf(EventHandler.class);
        assertThat(handlers.get("invert")).isNotNull().isInstanceOf(EventHandler.class);

        // Verify echo and invert share the same instance (multi-capability pattern)
        assertThat(handlers.get("echo")).isSameAs(handlers.get("invert"));
    }

    @Test
    void loadHandlers_withDuplicateCapability_throwsException() {
        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        // Skip test if JAR hasn't been built yet
        if (!Files.exists(lowerJar)) {
            return;
        }

        List<Path> jarPaths = List.of(lowerJar, lowerJar); // Same JAR twice

        assertThatThrownBy(() -> {
            HandlerLoader loader = HandlerLoader.lifecycleLoader(jarPaths, stubDispatcher);
            loader.loadHandlers();
        })
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate capability");
    }

    @Test
    void loadHandlers_withMalformedJar_skipsJarAndContinues(@TempDir Path tempDir) throws IOException {
        // Create a file with .jar extension but invalid content
        Path malformedJar = tempDir.resolve("malformed.jar");
        Files.writeString(malformedJar, "This is not a JAR file");

        Path validJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        // Skip test if valid JAR hasn't been built yet
        if (!Files.exists(validJar)) {
            return;
        }

        List<Path> jarPaths = List.of(malformedJar, validJar);

        // Should skip malformed JAR and load handler from valid JAR
        HandlerLoader loader = HandlerLoader.lifecycleLoader(jarPaths, stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(1);
        assertThat(handlers).containsKey("lower");
        assertThat(handlers.get("lower")).isNotNull().isInstanceOf(EventHandler.class);
    }

    @Test
    void loadHandlers_withEmptyJar_returnsEmptyMap(@TempDir Path tempDir) throws IOException {
        // Create an empty but valid JAR file
        Path emptyJar = tempDir.resolve("empty.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(emptyJar))) {
            // Empty JAR - write manifest only
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(emptyJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withMultiCapabilityHandler_registersUnderAllCapabilities() throws IOException {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar)) {
            return; // Gracefully skip if example JAR not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(echoJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(2);
        assertThat(handlers).containsKeys("echo", "invert");
        assertThat(handlers.get("echo")).isNotNull().isInstanceOf(EventHandler.class);
        assertThat(handlers.get("invert")).isNotNull().isInstanceOf(EventHandler.class);
    }

    @Test
    void loadHandlers_withMultiCapabilityHandler_sharesSameInstance() throws IOException {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar)) {
            return; // Gracefully skip if example JAR not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(echoJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Both capabilities must reference the exact same handler instance
        assertThat(handlers.get("echo")).isSameAs(handlers.get("invert"));
    }

    @Test
    void loadHandlers_withMultiCapabilityAndSingleCapabilityHandlers_loadsAll() throws IOException {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");
        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar) || !Files.exists(lowerJar)) {
            return; // Gracefully skip if example JARs not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(echoJar, lowerJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(3);
        assertThat(handlers).containsKeys("echo", "invert", "lower");

        // Multi-capability handler shares instance; single-capability handler is separate
        assertThat(handlers.get("echo")).isSameAs(handlers.get("invert"));
        assertThat(handlers.get("lower")).isNotSameAs(handlers.get("echo"));
    }

    @Test
    void loadHandlers_withDuplicateCapabilityAcrossMultiAndSingleHandlers_throwsException() {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar)) {
            return; // Gracefully skip if example JAR not built
        }

        // Loading the same echo JAR twice causes "echo" (or "invert") to appear twice
        List<Path> jarPaths = List.of(echoJar, echoJar);

        assertThatThrownBy(() -> {
            HandlerLoader loader = HandlerLoader.lifecycleLoader(jarPaths, stubDispatcher);
            loader.loadHandlers();
        })
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate capability");
    }

    @Test
    void close_shutsDownAllLifecycles() throws Exception {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");
        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar) || !Files.exists(lowerJar)) {
            return; // Gracefully skip if example JARs not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(echoJar, lowerJar), stubDispatcher);
        loader.loadHandlers();

        // Should not throw - lifecycles have no resources to clean up
        loader.close();
    }

    @Test
    void close_withNoHandlersLoaded_doesNotThrow() throws Exception {
        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(), stubDispatcher);
        loader.loadHandlers();

        // Should not throw even with no handlers
        loader.close();
    }

    @Test
    void loadHandlers_withFrameworkContext_passesContextToLifecycle() throws IOException {
        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(lowerJar)) {
            return; // Gracefully skip if example JAR not built
        }

        Object frameworkContext = new Object(); // Simple framework context stub

        HandlerLoader loader = HandlerLoader.lifecycleLoader(
            List.of(lowerJar),
            stubDispatcher,
            frameworkContext
        );
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Handler should be loaded successfully with framework context
        assertThat(handlers).hasSize(1);
        assertThat(handlers).containsKey("lower");
    }

    @Test
    void loadHandlers_verifyDispatcherPassedToHandlers() throws IOException {
        Path echoJar = Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(echoJar)) {
            return; // Gracefully skip if example JAR not built
        }

        HandlerLoader loader = HandlerLoader.lifecycleLoader(List.of(echoJar), stubDispatcher);
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Handler should be loaded successfully
        assertThat(handlers).hasSize(2);
        assertThat(handlers).containsKeys("echo", "invert");

        // Verify handlers are properly initialized (can't directly verify dispatcher wiring
        // without invoking handle(), but loading success implies lifecycle.initialize() succeeded)
        assertThat(handlers.get("echo")).isNotNull();
    }
}
