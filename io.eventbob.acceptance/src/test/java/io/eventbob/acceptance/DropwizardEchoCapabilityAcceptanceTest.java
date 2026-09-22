package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dropwizard realization of {@link EchoCapabilityAcceptanceTest}, run against a real Docker
 * container running the already-packaged {@code io.eventbob.example.microlith.dw.echo} and
 * {@code .dw.upper} fat jars as two OS processes sharing one container's loopback interface.
 */
@Testcontainers
class DropwizardEchoCapabilityAcceptanceTest extends EchoCapabilityAcceptanceTest {

  @Container
  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(Images.dwEchoAndUpper())
          .withExposedPorts(Images.DW_ECHO_PORT, Images.DW_UPPER_PORT)
          .waitingFor(Wait.forListeningPorts(Images.DW_ECHO_PORT, Images.DW_UPPER_PORT));

  @Override
  protected GenericContainer<?> container() {
    return CONTAINER;
  }

  @Override
  protected int echoPort() {
    return Images.DW_ECHO_PORT;
  }
}
