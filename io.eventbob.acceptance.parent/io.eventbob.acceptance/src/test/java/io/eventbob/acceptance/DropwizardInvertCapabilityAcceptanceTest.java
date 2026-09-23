package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dropwizard realization of {@link InvertCapabilityAcceptanceTest}, run against a real Docker
 * container built from the already-packaged {@code io.eventbob.example.microlith.dw.echo} fat
 * jar.
 */
@Testcontainers
class DropwizardInvertCapabilityAcceptanceTest extends InvertCapabilityAcceptanceTest {

  @Container
  private static final GenericContainer<?> CONTAINER = new GenericContainer<>(Images.dwEcho())
      .withExposedPorts(Images.DW_ECHO_PORT)
      .waitingFor(Wait.forListeningPort());

  @Override
  protected GenericContainer<?> container() {
    return CONTAINER;
  }

  @Override
  protected int port() {
    return Images.DW_ECHO_PORT;
  }
}
