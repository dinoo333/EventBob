package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring realization of {@link HealthcheckAcceptanceTest}, run against a real Docker container
 * built from the already-packaged {@code io.eventbob.example.microlith.spring.echo} fat jar.
 */
@Testcontainers
class SpringHealthcheckAcceptanceTest extends HealthcheckAcceptanceTest {

  @Container
  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(Images.springEcho())
          .withExposedPorts(Images.SPRING_ECHO_PORT)
          .waitingFor(Wait.forListeningPort());

  @Override
  protected GenericContainer<?> container() {
    return CONTAINER;
  }

  @Override
  protected int port() {
    return Images.SPRING_ECHO_PORT;
  }
}
