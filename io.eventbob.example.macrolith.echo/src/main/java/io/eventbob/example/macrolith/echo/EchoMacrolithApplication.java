package io.eventbob.example.macrolith.echo;

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
 * Echo macrolith application.
 *
 * <p>This concrete macrolith provides echo and lower capabilities by loading
 * their handler JARs. It demonstrates how to configure a specific EventBob
 * deployment by specifying which handlers to load.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.macrolith.echo"})
@Import(EventBobConfig.class)
public class EchoMacrolithApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoMacrolithApplication.class, args);
  }

  /**
   * Provide the list of handler JAR paths to load.
   *
   * <p>This configuration specifies which capabilities this macrolith supports.
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
