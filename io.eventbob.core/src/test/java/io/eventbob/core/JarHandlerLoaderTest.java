package io.eventbob.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JarHandlerLoaderTest {

    @Test
    void loadHandlers_withMissingJar_throwsIOException(@TempDir Path tempDir) {
        Path missingJar = tempDir.resolve("missing.jar");

        assertThatThrownBy(() -> {
            HandlerLoader loader = HandlerLoader.jarLoader(List.of(missingJar));
            loader.loadHandlers();
        })
            .isInstanceOf(IOException.class)
            .hasMessageContaining("JAR file not found");
    }

    @Test
    void loadHandlers_withEmptyList_returnsEmptyMap() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader(List.of());

        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withNonReadableFile_throwsIOException(@TempDir Path tempDir) throws IOException {
        Path nonReadableFile = tempDir.resolve("nonreadable.jar");
        Files.createFile(nonReadableFile);

        // Make file non-readable (note: this may not work on all platforms)
        if (nonReadableFile.toFile().setReadable(false)) {
            assertThatThrownBy(() -> {
                HandlerLoader loader = HandlerLoader.jarLoader(List.of(nonReadableFile));
                loader.loadHandlers();
            })
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not readable");
        }
    }

    @Test
    void loadHandlers_withValidJars_loadsAndInstantiatesHandlers() throws IOException {
        List<Path> jarPaths = List.of(
            Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
            Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
        );

        // Skip test if JARs haven't been built yet
        if (!Files.exists(jarPaths.get(0)) || !Files.exists(jarPaths.get(1))) {
            return; // Gracefully skip if example JARs not built
        }

        HandlerLoader loader = HandlerLoader.jarLoader(jarPaths);
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
            HandlerLoader loader = HandlerLoader.jarLoader(jarPaths);
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
        HandlerLoader loader = HandlerLoader.jarLoader(jarPaths);
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

        HandlerLoader loader = HandlerLoader.jarLoader(List.of(emptyJar));
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withMultiCapabilityHandler_registersUnderAllCapabilities() throws IOException {
        Path multiJar = Paths.get("io.eventbob.example.multi/target/io.eventbob.example.multi-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(multiJar)) {
            return; // Gracefully skip if example JAR not built
        }

        HandlerLoader loader = HandlerLoader.jarLoader(List.of(multiJar));
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(2);
        assertThat(handlers).containsKeys("to-upper", "to-lower");
        assertThat(handlers.get("to-upper")).isNotNull().isInstanceOf(EventHandler.class);
        assertThat(handlers.get("to-lower")).isNotNull().isInstanceOf(EventHandler.class);
    }

    @Test
    void loadHandlers_withMultiCapabilityHandler_sharesSameInstance() throws IOException {
        Path multiJar = Paths.get("io.eventbob.example.multi/target/io.eventbob.example.multi-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(multiJar)) {
            return; // Gracefully skip if example JAR not built
        }

        HandlerLoader loader = HandlerLoader.jarLoader(List.of(multiJar));
        Map<String, EventHandler> handlers = loader.loadHandlers();

        // Both capabilities must reference the exact same handler instance
        assertThat(handlers.get("to-upper")).isSameAs(handlers.get("to-lower"));
    }

    @Test
    void loadHandlers_withMultiCapabilityAndSingleCapabilityHandlers_loadsAll() throws IOException {
        Path multiJar = Paths.get("io.eventbob.example.multi/target/io.eventbob.example.multi-1.0.0-SNAPSHOT.jar");
        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(multiJar) || !Files.exists(lowerJar)) {
            return; // Gracefully skip if example JARs not built
        }

        HandlerLoader loader = HandlerLoader.jarLoader(List.of(multiJar, lowerJar));
        Map<String, EventHandler> handlers = loader.loadHandlers();

        assertThat(handlers).hasSize(3);
        assertThat(handlers).containsKeys("to-upper", "to-lower", "lower");

        // Multi-capability handler shares instance; single-capability handler is separate
        assertThat(handlers.get("to-upper")).isSameAs(handlers.get("to-lower"));
        assertThat(handlers.get("lower")).isNotSameAs(handlers.get("to-upper"));
    }

    @Test
    void loadHandlers_withDuplicateCapabilityAcrossMultiAndSingleHandlers_throwsException() {
        // The multi handler declares "to-lower". If we also load a JAR with a handler
        // declaring the same capability, it should throw.
        Path multiJar = Paths.get("io.eventbob.example.multi/target/io.eventbob.example.multi-1.0.0-SNAPSHOT.jar");

        if (!Files.exists(multiJar)) {
            return; // Gracefully skip if example JAR not built
        }

        // Loading the same multi JAR twice causes "to-upper" (or "to-lower") to appear twice
        List<Path> jarPaths = List.of(multiJar, multiJar);

        assertThatThrownBy(() -> {
            HandlerLoader loader = HandlerLoader.jarLoader(jarPaths);
            loader.loadHandlers();
        })
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate capability");
    }
}
