package io.eventbob.acceptance.broken.common;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;

/**
 * Lifecycle whose {@link #initialize(LifecycleContext)} always throws, used by the "Broken"
 * acceptance-test applications to force the real production startup-abort-on-lifecycle-failure
 * path: {@code EventBobBundle} / {@code EventBobConfig} do not catch initialization failures,
 * so this propagates out of bootstrap exactly as a real misbehaving handler's lifecycle would.
 */
public class FailingHandlerLifecycle extends HandlerLifecycle {

  @Override
  public void initialize(LifecycleContext context) {
    throw new IllegalStateException("Simulated lifecycle initialization failure");
  }

  @Override
  public EventHandler getHandler() {
    throw new IllegalStateException("getHandler() called without successful initialize()");
  }

  @Override
  public void shutdown() {
    // Never initialized: nothing to shut down.
  }
}
