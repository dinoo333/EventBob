package io.eventbob.acceptance.broken.common;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import io.eventbob.example.echo.EchoHandler;
import io.eventbob.example.echo.EchoService;

/**
 * Lifecycle that wires the shared {@link EchoHandler}, the same way the real
 * {@code EchoHandlerLifecycle} classes in the Dropwizard and Spring microlith example modules
 * do (both of those are, deliberately, near-identical thin wrappers with no framework-specific
 * code).
 *
 * <p>Registering two instances of this lifecycle with {@code EventBobBundle} /
 * {@code EventBobConfig} forces the real production duplicate-capability-detection path
 * ({@code IllegalStateException}) used by the "Broken" acceptance-test applications, without
 * this test module needing a compile-time dependency on the per-framework
 * {@code io.eventbob.example.echo.EchoHandlerLifecycle} classes (which would collide: the
 * Dropwizard and Spring microlith example modules both declare a class at that exact same
 * fully-qualified name, so depending on both jars for their classes in one compilation unit
 * would be an ambiguous split-package classpath).
 */
public class DuplicateEchoHandlerLifecycle extends HandlerLifecycle {
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
    handler = null;
  }
}
