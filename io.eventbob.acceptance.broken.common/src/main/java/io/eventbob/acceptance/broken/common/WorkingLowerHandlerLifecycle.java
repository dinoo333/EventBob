package io.eventbob.acceptance.broken.common;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import io.eventbob.example.lower.LowerHandler;
import io.eventbob.example.lower.LowerService;

/**
 * Lifecycle that wires the shared {@link LowerHandler} and always succeeds, including on
 * {@link #shutdown()}.
 *
 * <p>Paired with {@link FailingOnShutdownHandlerLifecycle} for the {@code shutdown-failure}
 * {@code BROKEN_MODE}: registers a distinct capability ("lower", not "echo"/"invert") so the
 * two lifecycles do not also trigger duplicate-capability-detection, letting the
 * shutdown-continues-after-one-holder's-error scenario be observed in isolation. This lifecycle
 * being shut down (or not) despite the other holder's error is the "shutdown continues for the
 * remaining holders" evidence.
 */
public class WorkingLowerHandlerLifecycle extends HandlerLifecycle {
  private LowerHandler handler;

  @Override
  public void initialize(LifecycleContext context) {
    handler = new LowerHandler(new LowerService());
  }

  @Override
  public EventHandler getHandler() {
    return handler;
  }

  @Override
  public void shutdown() {
    handler = null;
  }
}
