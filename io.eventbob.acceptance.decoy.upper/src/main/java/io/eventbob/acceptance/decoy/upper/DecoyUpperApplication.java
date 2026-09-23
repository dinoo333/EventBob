package io.eventbob.acceptance.decoy.upper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Standalone decoy standing in for the real "upper" remote capability process inside a
 * {@code RemoteCapabilityFailureAcceptanceTest} container.
 *
 * <p>Selects its behavior at process start from two environment variables:
 *
 * <ul>
 *   <li>{@code MODE} - one of {@link #MODE_FIXED_404}, {@link #MODE_FIXED_500},
 *       {@link #MODE_UNREACHABLE}, {@link #MODE_DELAYED_PAST_1000MS}.
 *   <li>{@code PORT} - the port to listen on (unused, and never opened, in {@link
 *       #MODE_UNREACHABLE}).
 * </ul>
 *
 * <p>Uses only {@link HttpServer}, the JDK's own built-in HTTP server - no EventBob, Dropwizard,
 * or Spring dependency - because the real "upper" application can never itself produce a
 * non-2xx HTTP status or a network failure (EventBob#processEvent's {@code exceptionally()}
 * always converts handler exceptions into a normally-resolved 200 response). A genuine
 * HTTP-error / network-failure / slow-response condition therefore requires a process that runs
 * no EventBob code at all.
 */
public final class DecoyUpperApplication {

  /** Responds immediately with HTTP 404. */
  public static final String MODE_FIXED_404 = "fixed-404";

  /** Responds immediately with HTTP 500. */
  public static final String MODE_FIXED_500 = "fixed-500";

  /**
   * Never opens its listening port at all, so any connection attempt fails at the network level.
   */
  public static final String MODE_UNREACHABLE = "unreachable";

  /** Sleeps past EchoService's 1000ms dispatch timeout before responding with HTTP 200. */
  public static final String MODE_DELAYED_PAST_1000MS = "delayed-past-1000ms";

  private static final long DELAY_PAST_TIMEOUT_MILLIS = 1500L;

  private DecoyUpperApplication() {
  }

  /**
   * Process entry point.
   *
   * @param args unused
   * @throws IOException if the HTTP listener cannot be started
   */
  public static void main(String[] args) throws IOException {
    String mode = requireEnv("MODE");

    if (MODE_UNREACHABLE.equals(mode)) {
      // Deliberately never starts a listener: the point of this mode is that the port is never
      // open in the container, so an HTTP client attempt fails with a connection error.
      return;
    }

    int port = Integer.parseInt(requireEnv("PORT"));
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", exchange -> {
      try {
        handle(mode, exchange);
      } finally {
        exchange.close();
      }
    });
    server.setExecutor(null);
    server.start();
  }

  private static void handle(String mode, HttpExchange exchange) throws IOException {
    switch (mode) {
      case MODE_FIXED_404 -> respond(exchange, 404, "decoy: not found");
      case MODE_FIXED_500 -> respond(exchange, 500, "decoy: internal error");
      case MODE_DELAYED_PAST_1000MS -> {
        sleep(DELAY_PAST_TIMEOUT_MILLIS);
        respond(exchange, 200, "decoy: delayed response");
      }
      default -> throw new IllegalArgumentException("Unknown MODE value: " + mode);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread
          .currentThread()
          .interrupt();
    }
  }

  private static void respond(HttpExchange exchange, int statusCode, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (OutputStream responseBody = exchange.getResponseBody()) {
      responseBody.write(bytes);
    }
  }

  private static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required environment variable '" + name + "' is not set");
    }
    return value;
  }
}
