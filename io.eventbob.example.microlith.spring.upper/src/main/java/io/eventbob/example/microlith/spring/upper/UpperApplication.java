package io.eventbob.example.microlith.spring.upper;

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
 * Upper microlith application.
 *
 * <p>This Spring Boot microlith provides upper capability by loading
 * its handler JAR. It demonstrates how to configure a specific EventBob
 * deployment by specifying which handlers to load.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.upper"})
@Import(EventBobConfig.class)
public class UpperApplication {

  public static void main(String[] args) {
    SpringApplication.run(UpperApplication.class, args);
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
        Paths.get("io.eventbob.example.upper-spring/target/io.eventbob.example.upper-spring-1.0.0-SNAPSHOT.jar")
    );
  }
}
