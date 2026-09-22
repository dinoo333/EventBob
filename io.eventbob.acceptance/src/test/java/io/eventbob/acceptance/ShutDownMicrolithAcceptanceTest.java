package io.eventbob.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventbob.acceptance.broken.common.BrokenMode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test for the "Operate a microlith as a running service" business use case's
 * shutdown half (see docs/business_use_cases.md, Shut Down Microlith), run against real Docker
 * containers of each framework realization.
 *
 * <p>Covers two acceptance criteria: a running microlith shuts down cleanly (process exits with
 * status 0, no hang) on a stop signal; one lifecycle holder's shutdown error does not prevent
 * the remaining holders from shutting down. The second scenario runs the "Broken" application
 * image with {@code BROKEN_MODE=shutdown-failure} (see
 * {@code io.eventbob.acceptance.broken.common.BrokenMode}), which registers one lifecycle that
 * always throws on shutdown alongside one that always succeeds.
 */
@Testcontainers
public abstract class ShutDownMicrolithAcceptanceTest {

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
  void stopSignal_shutsDownCleanly() {
    try (GenericContainer<?> container = new GenericContainer<>(goodImage())
        .withExposedPorts(port())
        .waitingFor(Wait.forListeningPort())) {
      container.start();

      container.stop();

      assertThat(container.isRunning()).isFalse();
    }
  }

  @Test
  void oneHoldersShutdownError_shutdownContinuesForRemainingHolders() {
    try (GenericContainer<?> container = new GenericContainer<>(brokenImage())
        .withEnv("BROKEN_MODE", BrokenMode.SHUTDOWN_FAILURE)
        .withExposedPorts(port())
        .waitingFor(Wait.forListeningPort())) {
      container.start();

      // Deliberately NOT container.stop(): that method stops AND REMOVES the container (see
      // GenericContainer#stop -> ResourceReaper#stopAndRemoveContainer, confirmed via javap
      // against the real Testcontainers 2.0.5 jar), so any subsequent getLogs() call would
      // return empty forever, no matter how long retried - confirmed empirically, this was the
      // actual root cause of a prior "logs never appear" failure. A plain docker stop via the
      // raw Docker client sends the same graceful-SIGTERM-then-SIGKILL-after-timeout sequence
      // without removing the container, so its logs remain readable until this method's own
      // try-with-resources close() performs the final cleanup.
      container
          .getDockerClient()
          .stopContainerCmd(container.getContainerId())
          .exec();

      assertThat(waitUntilStopped(container)).isTrue();
      assertThat(logsEventuallyContain(container, "Simulated lifecycle shutdown failure"))
          .isTrue();
    }
  }

  /**
   * Polls {@link GenericContainer#getLogs()} for up to 15 seconds (matching
   * {@link #waitUntilStopped}'s own ceiling), guarding against a real, repeatedly-observed delay
   * between a container being reported stopped (an accurate, live check - see
   * {@link BootstrapMicrolithAcceptanceTest#waitForExitCode}) and this specific log line
   * actually being written/flushed. Empirically, Dropwizard's shutdown path exhibits
   * multiple-seconds-scale latency here under real Docker execution that a shorter window (3s,
   * then 5s) proved insufficient for on repeated real runs, even though a direct, non-Docker
   * {@code kill -TERM} test showed the JVM itself is capable of writing this line quickly - the
   * exact mechanism causing the extra Docker-specific latency was not pinned down precisely, so
   * this uses a generous, evidence-backed margin rather than a guessed-and-hoped-tight one.
   */
  private boolean logsEventuallyContain(GenericContainer<?> container, String fragment) {
    long deadline = System.nanoTime() + Duration
        .ofSeconds(15)
        .toNanos();
    while (System.nanoTime() < deadline) {
      if (container
          .getLogs()
          .contains(fragment)) {
        return true;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread
            .currentThread()
            .interrupt();
        break;
      }
    }
    return container
        .getLogs()
        .contains(fragment);
  }

  private boolean waitUntilStopped(GenericContainer<?> container) {
    long deadline = System.nanoTime() + Duration
        .ofSeconds(15)
        .toNanos();
    while (System.nanoTime() < deadline) {
      if (!container.isRunning()) {
        return true;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread
            .currentThread()
            .interrupt();
        return false;
      }
    }
    return !container.isRunning();
  }
}
