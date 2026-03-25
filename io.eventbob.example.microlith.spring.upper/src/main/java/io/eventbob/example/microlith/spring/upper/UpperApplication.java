package io.eventbob.example.microlith.spring.upper;

import io.eventbob.example.upper.UpperHandlerLifecycle;
import io.eventbob.spring.EventBobConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Upper microlith application.
 *
 * <p>This Spring Boot microlith provides the upper capability via inline
 * lifecycle wiring. It demonstrates the microlithic microservice pattern
 * for a single-capability deployment.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.example.microlith.spring.upper"})
@Import(EventBobConfig.class)
public class UpperApplication {

  public static void main(String[] args) {
    SpringApplication.run(UpperApplication.class, args);
  }

  @Bean
  public UpperHandlerLifecycle upperHandlerLifecycle() {
    return new UpperHandlerLifecycle();
  }
}
