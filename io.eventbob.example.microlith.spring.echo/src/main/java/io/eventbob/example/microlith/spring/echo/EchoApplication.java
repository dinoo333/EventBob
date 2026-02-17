package io.eventbob.example.microlith.spring.echo;

import io.eventbob.spring.EventBobConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Echo microlith application.
 *
 * <p>This Spring Boot microlith provides echo and lower capabilities by loading
 * their handler JARs. It demonstrates how to configure a specific EventBob
 * deployment by specifying which handlers to load.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.echo"})
@Import(EventBobConfig.class)
public class EchoApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
  }

  /**
   * Provide the list of handler JAR paths to load.
   *
   * <p>This configuration specifies which capabilities this microlith supports.
   * JAR paths are relative to the project root directory.
   *
   * @return list of paths to handler JAR files
   */
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("io.eventbob.example.echo/target/io.eventbob.example.echo-1.0.0-SNAPSHOT.jar"),
        Paths.get("io.eventbob.example.lower/target/io.eventbob.example.lower-1.0.0-SNAPSHOT.jar")
    );
  }
}
