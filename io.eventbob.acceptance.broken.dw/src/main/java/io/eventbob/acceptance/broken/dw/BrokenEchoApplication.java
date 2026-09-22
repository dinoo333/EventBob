package io.eventbob.acceptance.broken.dw;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.eventbob.acceptance.broken.common.BrokenMode;
import io.eventbob.dropwizard.EventBobBundle;

/**
 * Dropwizard application that registers a deliberately-broken set of inline lifecycles
 * (selected via the {@code BROKEN_MODE} environment variable - see {@link BrokenMode}),
 * forcing one of {@link EventBobBundle}'s two real production bootstrap-abort paths:
 * duplicate-capability-detection or lifecycle-initialization-failure.
 *
 * <p>Used by {@code BootstrapMicrolithAcceptanceTest}'s Dropwizard subclass to prove that
 * startup aborts (the process exits without accepting requests) in both cases. This is
 * test-only glue: it exercises {@link EventBobBundle} exactly as {@code EchoApplication} does,
 * with no production source changes.
 */
public class BrokenEchoApplication extends Application<Configuration> {

  private final EventBobBundle bundle = new EventBobBundle(
      null,
      BrokenMode.lifecycles(),
      null
  );

  /**
   * Main method.
   *
   * @param args Arguments.
   * @throws Exception If anything goes wrong (expected: a bootstrap-abort exception).
   */
  public static void main(String[] args) throws Exception {
    new BrokenEchoApplication().run(args);
  }

  @Override
  public void initialize(Bootstrap<Configuration> bootstrap) {
    bootstrap.addBundle(bundle);
  }

  @Override
  public void run(Configuration configuration, Environment environment) {
    // All wiring is handled by EventBobBundle.run(), which is expected to throw here.
  }
}
