package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring realization of {@link BootstrapMicrolithAcceptanceTest}.
 */
@Testcontainers
class SpringBootstrapMicrolithAcceptanceTest extends BootstrapMicrolithAcceptanceTest {

  @Override
  protected ImageFromDockerfile goodImage() {
    return Images.springEcho();
  }

  @Override
  protected ImageFromDockerfile brokenImage() {
    return Images.springEchoBroken();
  }

  @Override
  protected int port() {
    return Images.SPRING_ECHO_PORT;
  }
}
