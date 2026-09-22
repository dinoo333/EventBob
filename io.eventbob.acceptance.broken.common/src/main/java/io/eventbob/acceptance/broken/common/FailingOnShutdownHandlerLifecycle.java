package io.eventbob.acceptance.broken.common;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import io.eventbob.example.echo.EchoHandler;
import io.eventbob.example.echo.EchoService;

/**
 * Lifecycle that initializes successfully (wiring the shared {@link EchoHandler}, exactly like
 * {@link DuplicateEchoHandlerLifecycle}) but always throws from {@link #shutdown()}.
 *
 * <p>Used by the "Broken" acceptance-test applications' {@code shutdown-failure}
 * {@code BROKEN_MODE} to force the real production
 * "shutdown continues after one holder's error" path: {@code EventBobBundle} /
 * {@code EventBobConfig} catch and log each lifecycle's shutdown exception individually and
 * proceed to the remaining holders, rather than aborting shutdown.
 */
public class FailingOnShutdownHandlerLifecycle extends HandlerLifecycle {
  private EchoHandler handler;

  @Override
  public void initialize(LifecycleContext context) {
    handler = new EchoHandler(new EchoService());
  }

  @Override
  public EventHandler getHandler() {
    return handler;
  }

  @Override
  public void shutdown() {
    throw new IllegalStateException("Simulated lifecycle shutdown failure");
  }
}
