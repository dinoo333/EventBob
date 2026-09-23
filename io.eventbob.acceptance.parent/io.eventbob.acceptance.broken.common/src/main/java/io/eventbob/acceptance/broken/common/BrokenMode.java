package io.eventbob.acceptance.broken.common;

import io.eventbob.core.HandlerLifecycle;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Selects which real production bootstrap/shutdown-abort path a "Broken" acceptance-test
 * application exercises, via the {@code BROKEN_MODE} environment variable set by the test that
 * starts the container. Both {@code BrokenEchoApplication} classes (Dropwizard and Spring)
 * share this so one broken-image build per framework covers all three abort scenarios instead
 * of building a separate image per scenario per framework.
 *
 * <p>Exposes {@link #first()} / {@link #second()} rather than a single {@code List} so callers
 * can register lifecycles the same way the real applications do: as individually-declared
 * instances (Dropwizard: {@link #lifecycles()} filters nulls into a {@code List}; Spring: one
 * {@code @Bean} method per slot, where a {@code null} return omits that bean - empirically
 * verified to correctly exclude that slot from {@code List<HandlerLifecycle>}
 * collection-autowiring - matching {@code EchoApplication}'s one-bean-per-lifecycle pattern
 * rather than a single list-typed bean).
 */
public final class BrokenMode {

  /** Registers "echo" twice, forcing the duplicate-capability-detection path. */
  public static final String DUPLICATE_CAPABILITY = "duplicate-capability";

  /** Registers one lifecycle whose initialize() throws, forcing the init-failure path. */
  public static final String LIFECYCLE_FAILURE = "lifecycle-failure";

  /**
   * Registers one lifecycle whose shutdown() throws alongside one that shuts down cleanly,
   * forcing the shutdown-continues-after-one-holder's-error path.
   */
  public static final String SHUTDOWN_FAILURE = "shutdown-failure";

  private static final String ENV_VAR = "BROKEN_MODE";

  private BrokenMode() {
  }

  /**
   * The first lifecycle to register, present in every mode.
   *
   * @return a fresh lifecycle instance for this process' configured {@code BROKEN_MODE}
   */
  public static HandlerLifecycle first() {
    return switch (mode()) {
      case LIFECYCLE_FAILURE -> new FailingHandlerLifecycle();
      case SHUTDOWN_FAILURE -> new FailingOnShutdownHandlerLifecycle();
      default -> new DuplicateEchoHandlerLifecycle();
    };
  }

  /**
   * The second lifecycle to register, or {@code null} when {@code BROKEN_MODE} calls for only
   * one lifecycle (the lifecycle-init-failure scenario).
   *
   * @return a fresh lifecycle instance, or {@code null}
   */
  public static HandlerLifecycle second() {
    return switch (mode()) {
      case LIFECYCLE_FAILURE -> null;
      case SHUTDOWN_FAILURE -> new WorkingLowerHandlerLifecycle();
      default -> new DuplicateEchoHandlerLifecycle();
    };
  }

  /**
   * All non-null lifecycles for this process' configured {@code BROKEN_MODE}, in registration
   * order.
   *
   * @return the lifecycle list to hand to {@code EventBobBundle}
   */
  public static List<HandlerLifecycle> lifecycles() {
    return Stream
        .of(first(), second())
        .filter(Objects::nonNull)
        .toList();
  }

  private static String mode() {
    String mode = System.getenv(ENV_VAR);
    if (mode == null || DUPLICATE_CAPABILITY.equals(mode) || LIFECYCLE_FAILURE.equals(mode)
        || SHUTDOWN_FAILURE.equals(mode)) {
      return mode == null ? DUPLICATE_CAPABILITY : mode;
    }
    throw new IllegalArgumentException(
        "Unknown " + ENV_VAR + " value: " + mode + " (expected '" + DUPLICATE_CAPABILITY
            + "', '" + LIFECYCLE_FAILURE + "' or '" + SHUTDOWN_FAILURE + "')");
  }
}
