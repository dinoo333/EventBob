package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring realization of {@link EchoCapabilityAcceptanceTest}, run against a real Docker
 * container running the already-packaged {@code io.eventbob.example.microlith.spring.echo} and
 * {@code .spring.upper} fat jars as two OS processes sharing one container's loopback interface.
 */
@Testcontainers
class SpringEchoCapabilityAcceptanceTest extends EchoCapabilityAcceptanceTest {

  @Container
  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(Images.springEchoAndUpper())
          .withExposedPorts(Images.SPRING_ECHO_PORT, Images.SPRING_UPPER_PORT)
          .waitingFor(Wait.forListeningPorts(Images.SPRING_ECHO_PORT, Images.SPRING_UPPER_PORT));

  @Override
  protected GenericContainer<?> container() {
    return CONTAINER;
  }

  @Override
  protected int echoPort() {
    return Images.SPRING_ECHO_PORT;
  }
}
