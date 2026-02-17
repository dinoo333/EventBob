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
        HandlerLoader loader = HandlerLoader.jarLoader();
        Path missingJar = tempDir.resolve("missing.jar");

        assertThatThrownBy(() -> loader.loadHandlers(List.of(missingJar)))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("JAR file not found");
    }

    @Test
    void loadHandlers_withEmptyList_returnsEmptyMap() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();

        Map<String, EventHandler> handlers = loader.loadHandlers(List.of());

        assertThat(handlers).isEmpty();
    }

    @Test
    void loadHandlers_withNonReadableFile_throwsIOException(@TempDir Path tempDir) throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();
        Path nonReadableFile = tempDir.resolve("nonreadable.jar");
        Files.createFile(nonReadableFile);

        // Make file non-readable (note: this may not work on all platforms)
        if (nonReadableFile.toFile().setReadable(false)) {
            assertThatThrownBy(() -> loader.loadHandlers(List.of(nonReadableFile)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not readable");
        }
    }

    @Test
    void loadHandlers_withValidJars_loadsAndInstantiatesHandlers() throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();

        List<Path> jarPaths = List.of(
            Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar"),
            Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar")
        );

        // Skip test if JARs haven't been built yet
        if (!Files.exists(jarPaths.get(0)) || !Files.exists(jarPaths.get(1))) {
            return; // Gracefully skip if example JARs not built
        }

        Map<String, EventHandler> handlers = loader.loadHandlers(jarPaths);

        assertThat(handlers).hasSize(2);
        assertThat(handlers).containsKeys("lower", "echo");
        
        // Verify handlers are actually instantiated (not null)
        assertThat(handlers.get("lower")).isNotNull().isInstanceOf(EventHandler.class);
        assertThat(handlers.get("echo")).isNotNull().isInstanceOf(EventHandler.class);
    }

    @Test
    void loadHandlers_withDuplicateCapability_throwsException() {
        HandlerLoader loader = HandlerLoader.jarLoader();

        Path lowerJar = Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar");

        // Skip test if JAR hasn't been built yet
        if (!Files.exists(lowerJar)) {
            return;
        }

        List<Path> jarPaths = List.of(lowerJar, lowerJar); // Same JAR twice

        assertThatThrownBy(() -> loader.loadHandlers(jarPaths))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate capability");
    }

    @Test
    void loadHandlers_withMalformedJar_skipsJarAndContinues(@TempDir Path tempDir) throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();

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
        Map<String, EventHandler> handlers = loader.loadHandlers(jarPaths);

        assertThat(handlers).hasSize(1);
        assertThat(handlers).containsKey("lower");
        assertThat(handlers.get("lower")).isNotNull().isInstanceOf(EventHandler.class);
    }

    @Test
    void loadHandlers_withEmptyJar_returnsEmptyMap(@TempDir Path tempDir) throws IOException {
        HandlerLoader loader = HandlerLoader.jarLoader();

        // Create an empty but valid JAR file
        Path emptyJar = tempDir.resolve("empty.jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(emptyJar))) {
            // Empty JAR - write manifest only
        }

        Map<String, EventHandler> handlers = loader.loadHandlers(List.of(emptyJar));

        assertThat(handlers).isEmpty();
    }
}
