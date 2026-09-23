package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.broken.common.BrokenMode;
import io.eventbob.acceptance.support.EventClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for the "Operate a microlith as a running service" business use case's
 * bootstrap half (see docs/business_use_cases.md, Bootstrap Microlith), run against real Docker
 * containers of each framework realization.
 *
 * <p>Covers three acceptance criteria: normal startup accepts requests; a lifecycle holder
 * failing to initialize aborts startup without accepting requests; two handler sources
 * declaring the same capability aborts startup without accepting requests. The two abort
 * scenarios run the same "Broken" application image with different {@code BROKEN_MODE}
 * environment values (see {@code io.eventbob.acceptance.broken.common.BrokenMode}).
 */
@Testcontainers
public abstract class BootstrapMicrolithAcceptanceTest {

  /**
   * The good (non-broken) image for this framework realization.
   *
   * @return an image built from the real, already-packaged fat jar
   */
  protected abstract ImageFromDockerfile goodImage();

  /**
   * The broken image for this framework realization (behavior selected by {@code BROKEN_MODE}).
   *
   * @return an image built from this module's own broken test jar
   */
  protected abstract ImageFromDockerfile brokenImage();

  /**
   * The internal port the good image's microlith listens on.
   *
   * @return the container-internal HTTP port
   */
  protected abstract int port();

  @Test
  void normalStartup_acceptsRequests() {
    try (GenericContainer<?> container = new GenericContainer<>(goodImage())
        .withExposedPorts(port())
        .waitingFor(Wait.forListeningPort())) {
      container.start();

      String eventsUrl = "http://" + container.getHost() + ":" + container.getMappedPort(port())
          + "/events";
      EventClient.Response response = EventClient.post(
          eventsUrl, Map.of("source", "test", "target", "healthcheck"));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body().get("payload")).isEqualTo(Boolean.TRUE);
    }
  }

  @Test
  void lifecycleInitializationFailure_abortsStartupWithoutAcceptingRequests() {
    assertAbortsStartup(BrokenMode.LIFECYCLE_FAILURE, "Simulated lifecycle initialization "
        + "failure");
  }

  @Test
  void duplicateCapability_abortsStartupWithoutAcceptingRequests() {
    assertAbortsStartup(BrokenMode.DUPLICATE_CAPABILITY, "Duplicate capability");
  }

  private void assertAbortsStartup(String brokenMode, String expectedLogFragment) {
    try (GenericContainer<?> container = new GenericContainer<>(brokenImage())
        .withEnv("BROKEN_MODE", brokenMode)
        .waitingFor(Wait
            .forLogMessage(".*(Exception|error|Error).*", 1)
            .withStartupTimeout(Duration.ofSeconds(60)))) {
      container.start();

      String logs = container.getLogs();
      assertThat(logs).contains(expectedLogFragment);
      assertThat(waitForExitCode(container)).isNotZero();
    }
  }

  /**
   * Polls docker inspect until the container has actually exited, guarding against the race
   * between the log line matched by {@link Wait#forLogMessage} appearing and the JVM inside the
   * container finishing its shutdown.
   *
   * <p>Deliberately reads the exit code via {@link GenericContainer#getCurrentContainerInfo()},
   * never the plain {@code getContainerInfo()}: the latter returns a snapshot cached from
   * container start (confirmed via the real Testcontainers 2.0.5 bytecode - {@code isRunning()}
   * itself already correctly uses {@code getCurrentContainerInfo()}, but a naive exit-code read
   * via the stale cached snapshot would always see exit code 0, the value captured while the
   * container was still starting up - verified empirically: a real container run against this
   * exact scenario reported exit code 0 through this stale accessor despite the process having
   * genuinely exited 1, confirmed via a direct, non-Testcontainers {@code docker run}).
   */
  private long waitForExitCode(GenericContainer<?> container) {
    long deadline = System.nanoTime() + Duration
        .ofSeconds(15)
        .toNanos();
    while (System.nanoTime() < deadline) {
      if (!container.isRunning()) {
        return container
            .getCurrentContainerInfo()
            .getState()
            .getExitCodeLong();
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread
            .currentThread()
            .interrupt();
        break;
      }
    }
    return container
        .getCurrentContainerInfo()
        .getState()
        .getExitCodeLong();
  }
}
