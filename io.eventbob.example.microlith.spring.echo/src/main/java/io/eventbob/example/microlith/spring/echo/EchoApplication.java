package io.eventbob.example.microlith.spring.echo;

import io.eventbob.spring.EventBobConfig;
import io.eventbob.spring.adapter.RemoteCapability;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Echo microlith application.
 *
 * <p>This Spring Boot microlith provides echo and lower capabilities locally,
 * and delegates to the upper microlith remotely via HTTP. It demonstrates:
 * </p>
 * <ul>
 *   <li>Loading local handlers from JAR files (echo, lower)</li>
 *   <li>Configuring remote handlers via HTTP endpoints (upper)</li>
 *   <li>Location transparency: EventBob treats both identically</li>
 * </ul>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.echo"})
@Import(EventBobConfig.class)
public class EchoApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
  }

  /**
   * Provide the list of local handler JAR paths to load.
   *
   * <p>This configuration specifies which capabilities this microlith provides locally.
   * JAR paths are relative to the project root directory.
   *
   * @return list of paths to handler JAR files
   */
  @Bean
  public List<Path> handlerJarPaths() {
    return List.of(
        Paths.get("io.eventbob.example.echo-spring/target/io.eventbob.example.echo-spring-1.0.0-SNAPSHOT.jar"),
        Paths.get("io.eventbob.example.lower-spring/target/io.eventbob.example.lower-spring-1.0.0-SNAPSHOT.jar")
    );
  }

  /**
   * Provide the list of remote capability endpoints.
   *
   * <p>This configuration specifies which capabilities this microlith delegates
   * to remote services. Each RemoteCapability maps a capability name to a remote
   * endpoint URI.
   *
   * @return list of remote capability endpoints
   */
  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://localhost:8081"))
    );
  }
}
