package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dropwizard realization of {@link RemoteCapabilityFailureAcceptanceTest}, run against a real
 * Docker container running the already-packaged {@code io.eventbob.example.microlith.dw.echo}
 * fat jar alongside this module's own {@code io.eventbob.acceptance.decoy.upper} fat jar (in
 * place of the real {@code dw.upper} jar).
 */
@Testcontainers
class DropwizardRemoteCapabilityFailureAcceptanceTest
    extends RemoteCapabilityFailureAcceptanceTest {

  @Override
  protected ImageFromDockerfile image() {
    return Images.dwEchoAndDecoyUpper();
  }

  @Override
  protected int echoPort() {
    return Images.DW_ECHO_PORT;
  }

  @Override
  protected int decoyPort() {
    return Images.DW_UPPER_PORT;
  }
}
