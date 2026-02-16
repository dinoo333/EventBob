package io.eventbob.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application entry point for EventBob server.
 *
 * <p>Starts an HTTP server with a REST endpoint at POST /events that processes
 * events through registered handlers. The application is configured via
 * {@link EventBobConfig} which wires the EventBob instance with handlers.
 */
@SpringBootApplication
public class EventBobApplication {
  public static void main(String[] args) {
    SpringApplication.run(EventBobApplication.class, args);
  }
}
