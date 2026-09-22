package io.eventbob.acceptance.broken.spring;

import io.eventbob.acceptance.broken.common.BrokenMode;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.spring.EventBobConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot application that registers a deliberately-broken set of inline lifecycles
 * (selected via the {@code BROKEN_MODE} environment variable - see {@link BrokenMode}),
 * forcing one of {@link EventBobConfig}'s two real production bootstrap-abort paths:
 * duplicate-capability-detection or lifecycle-initialization-failure.
 *
 * <p>Used by {@code BootstrapMicrolithAcceptanceTest}'s Spring subclass to prove that startup
 * aborts (the process exits without accepting requests) in both cases. This is test-only glue:
 * it exercises {@link EventBobConfig} exactly as {@code EchoApplication} does, with no
 * production source changes.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"io.eventbob.spring", "io.eventbob.acceptance.broken.spring"})
@Import(EventBobConfig.class)
public class BrokenEchoApplication {

  /**
   * Main method to start the (expected-to-fail) application.
   *
   * @param args App args.
   */
  public static void main(String[] args) {
    SpringApplication.run(BrokenEchoApplication.class, args);
  }

  /**
   * Define the first lifecycle bean, present in every {@code BROKEN_MODE}.
   *
   * @return a lifecycle instance per {@link BrokenMode#first()}
   */
  @Bean
  public HandlerLifecycle firstBrokenLifecycle() {
    return BrokenMode.first();
  }

  /**
   * Define the second lifecycle bean. Returning {@code null} here (the lifecycle-init-failure
   * {@code BROKEN_MODE}) omits this bean from the context, matching
   * {@link BrokenMode#second()}'s contract.
   *
   * @return a lifecycle instance per {@link BrokenMode#second()}, or {@code null}
   */
  @Bean
  public HandlerLifecycle secondBrokenLifecycle() {
    return BrokenMode.second();
  }
}
