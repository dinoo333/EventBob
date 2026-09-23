package io.eventbob.acceptance;

import io.eventbob.acceptance.support.Images;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring realization of {@link RemoteCapabilityFailureAcceptanceTest}, run against a real Docker
 * container running the already-packaged {@code io.eventbob.example.microlith.spring.echo} fat
 * jar alongside this module's own {@code io.eventbob.acceptance.decoy.upper} fat jar (in place of
 * the real {@code spring.upper} jar).
 *
 * <p>Spring's {@code EventController} wires {@code onError} as {@code (error, originalEvent) ->
 * null}, identically to Dropwizard's {@code EventResource} - confirmed by reading both classes -
 * so this subclass needs no assertion differences from the Dropwizard realization.
 */
@Testcontainers
class SpringRemoteCapabilityFailureAcceptanceTest extends RemoteCapabilityFailureAcceptanceTest {

  @Override
  protected ImageFromDockerfile image() {
    return Images.springEchoAndDecoyUpper();
  }

  @Override
  protected int echoPort() {
    return Images.SPRING_ECHO_PORT;
  }

  @Override
  protected int decoyPort() {
    return Images.SPRING_UPPER_PORT;
  }
}
