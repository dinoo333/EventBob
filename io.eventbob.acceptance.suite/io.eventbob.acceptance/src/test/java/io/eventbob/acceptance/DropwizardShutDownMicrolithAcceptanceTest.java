package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dropwizard realization of {@link ShutDownMicrolithAcceptanceTest}.
 */
@Testcontainers
class DropwizardShutDownMicrolithAcceptanceTest extends ShutDownMicrolithAcceptanceTest {

  @Override
  protected ImageFromDockerfile goodImage() {
    return Images.dwEcho();
  }

  @Override
  protected ImageFromDockerfile brokenImage() {
    return Images.dwEchoBroken();
  }

  @Override
  protected int port() {
    return Images.DW_ECHO_PORT;
  }
}
