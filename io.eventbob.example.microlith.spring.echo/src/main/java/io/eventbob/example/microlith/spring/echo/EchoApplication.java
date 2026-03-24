package io.eventbob.example.microlith.spring.echo;

import io.eventbob.example.echo.EchoHandlerLifecycle;
import io.eventbob.example.lower.LowerHandlerLifecycle;
import io.eventbob.spring.EventBobConfig;
import io.eventbob.spring.adapter.RemoteCapability;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.List;

/**
 * Echo microlith application.
 *
 * <p>This Spring Boot microlith provides echo, invert, and lower capabilities
 * locally via inline lifecycle wiring, and delegates upper to a remote microlith.
 * It demonstrates the microlithic microservice pattern: multiple capabilities
 * co-located in a single deployable unit.
 * </p>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.echo"})
@Import(EventBobConfig.class)
public class EchoApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
  }

  @Bean
  public EchoHandlerLifecycle echoHandlerLifecycle() {
    return new EchoHandlerLifecycle();
  }

  @Bean
  public LowerHandlerLifecycle lowerHandlerLifecycle() {
    return new LowerHandlerLifecycle();
  }

  @Bean
  public List<RemoteCapability> remoteCapabilities() {
    return List.of(
        new RemoteCapability("upper", URI.create("http://localhost:8081"))
    );
  }
}
