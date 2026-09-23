package io.eventbob.acceptance.support;

import java.nio.file.Path;
import java.util.Map;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Builds the six Testcontainers images this module's acceptance tests run against.
 *
 * <p>The good single-process images copy in an already-built fat jar (an opaque {@code COPY}
 * input, never a Java compile dependency) and run it with {@code java -jar}. The echo+upper
 * images copy in two fat jars (and, for Dropwizard, one already-existing config file) and run
 * both as OS processes sharing one container, to satisfy each {@code EchoApplication}'s
 * hardcoded {@code localhost} remote-capability URI. The broken images copy in one already-built,
 * single-framework fat jar (built by the dedicated {@code io.eventbob.acceptance.broken.dw} /
 * {@code .spring} modules - see those modules' pom.xml for why each framework needs its own
 * independently-shaded jar) and run it the same way the good images do. All file locations are
 * supplied as system properties by the {@code maven-failsafe-plugin} configuration in this
 * module's {@code pom.xml}. No image build step touches production source.
 */
public final class Images {

  /** Application port the good Dropwizard image listens on (Dropwizard's own default). */
  public static final int DW_ECHO_PORT = 8080;

  /** Application port the good Spring image listens on ({@code application.properties}). */
  public static final int SPRING_ECHO_PORT = 8080;

  /**
   * Port the "upper" remote capability listens on inside the Dropwizard echo+upper image,
   * matching the literal {@code http://localhost:8082} the Dropwizard {@code EchoApplication}
   * hardcodes for its {@code RemoteCapability}.
   */
  public static final int DW_UPPER_PORT = 8082;

  /**
   * Port the "upper" remote capability listens on inside the Spring echo+upper image, matching
   * the literal {@code http://localhost:8081} the Spring {@code EchoApplication} hardcodes for
   * its {@code RemoteCapability}.
   */
  public static final int SPRING_UPPER_PORT = 8081;

  /**
   * Marker label stamped on every image this class builds, so a host-level cleanup step (see
   * this module's {@code pom.xml}, {@code maven-antrun-plugin} bound to {@code
   * post-integration-test}) can find and remove them by {@code docker images --filter
   * label=io.eventbob.acceptance=true} regardless of whether the test JVM shuts down cleanly.
   * This is a supplement to, not a replacement for, Testcontainers' own Ryuk-based reaping (see
   * {@link ImageFromDockerfile}'s {@code deleteOnExit}, left at its default {@code true}): Ryuk's
   * cleanup depends on the Ryuk container being enabled and reachable, which this module does not
   * control, so a Maven-process-owned fallback closes that gap independently of the JVM.
   */
  private static final Map<String, String> CLEANUP_LABEL = Map.of("io.eventbob.acceptance",
      "true");

  private Images() {
  }

  /**
   * Good Dropwizard echo microlith image: echo/invert/lower/healthcheck capabilities,
   * listening on {@link #DW_ECHO_PORT} using Dropwizard's built-in default connector port.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile dwEcho() {
    return jarImage(path("eventbob.acceptance.dwEchoJar"), "server");
  }

  /**
   * Good Spring echo microlith image: echo/invert/lower/healthcheck capabilities, listening on
   * {@link #SPRING_ECHO_PORT} per {@code application.properties}.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile springEcho() {
    return jarImage(path("eventbob.acceptance.springEchoJar"));
  }

  /**
   * Dropwizard echo+upper image: runs the already-packaged {@code
   * io.eventbob.example.microlith.dw.echo} and {@code .dw.upper} fat jars as two real OS
   * processes sharing this one container's loopback interface, listening on {@link
   * #DW_ECHO_PORT} and {@link #DW_UPPER_PORT} respectively - the only way to satisfy the
   * Dropwizard {@code EchoApplication}'s hardcoded {@code http://localhost:8082} remote
   * capability URI without changing it. {@code upper} is started first, backgrounded with
   * {@code &}, then {@code echo} replaces the shell via {@code exec} so it becomes the
   * container's foreground process without killing the already-backgrounded {@code upper}.
   * {@code upper}'s own already-existing, unmodified {@code config.yml} is copied in as an
   * opaque input so its app/admin connector ports match what {@code EchoApplication} expects.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile dwEchoAndUpper() {
    return new ImageFromDockerfile()
        .withFileFromPath("echo.jar", path("eventbob.acceptance.dwEchoJar"))
        .withFileFromPath("upper.jar", path("eventbob.acceptance.dwUpperJar"))
        .withFileFromPath("upper-config.yml", path("eventbob.acceptance.dwUpperConfig"))
        .withDockerfileFromBuilder(builder -> builder
            .from("eclipse-temurin:21-jre")
            .copy("echo.jar", "/echo.jar")
            .copy("upper.jar", "/upper.jar")
            .copy("upper-config.yml", "/upper-config.yml")
            .entryPoint("sh", "-c",
                "java -jar /upper.jar server /upper-config.yml & exec java -jar /echo.jar "
                    + "server"))
        .withBuildImageCmdModifier(cmd -> cmd.withLabels(CLEANUP_LABEL));
  }

  /**
   * Spring echo+upper image: runs the already-packaged {@code
   * io.eventbob.example.microlith.spring.echo} and {@code .spring.upper} fat jars as two real
   * OS processes sharing this one container's loopback interface, listening on {@link
   * #SPRING_ECHO_PORT} and {@link #SPRING_UPPER_PORT} respectively - the only way to satisfy the
   * Spring {@code EchoApplication}'s hardcoded {@code http://localhost:8081} remote capability
   * URI without changing it. {@code upper} needs no config copied in: Spring Boot auto-loads its
   * own bundled {@code application.properties} (port 8081) with zero CLI args.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile springEchoAndUpper() {
    return new ImageFromDockerfile()
        .withFileFromPath("echo.jar", path("eventbob.acceptance.springEchoJar"))
        .withFileFromPath("upper.jar", path("eventbob.acceptance.springUpperJar"))
        .withDockerfileFromBuilder(builder -> builder
            .from("eclipse-temurin:21-jre")
            .copy("echo.jar", "/echo.jar")
            .copy("upper.jar", "/upper.jar")
            .entryPoint("sh", "-c", "java -jar /upper.jar & exec java -jar /echo.jar"))
        .withBuildImageCmdModifier(cmd -> cmd.withLabels(CLEANUP_LABEL));
  }

  /**
   * Dropwizard echo+decoy-upper image: runs the already-packaged {@code
   * io.eventbob.example.microlith.dw.echo} fat jar alongside this module's own non-EventBob
   * {@code io.eventbob.acceptance.decoy.upper} fat jar (instead of the real {@code dw.upper} jar)
   * as two OS processes sharing this one container's loopback interface, on the same {@link
   * #DW_UPPER_PORT} the Dropwizard {@code EchoApplication}'s hardcoded remote capability URI
   * expects. The decoy's {@code PORT} is baked in at image-build time (it is fixed by this
   * framework's hardcoded URI); its {@code MODE} is left unset here and must be supplied per
   * test via {@code GenericContainer#withEnv("MODE", ...)} before {@code container.start()} -
   * see {@code RemoteCapabilityFailureAcceptanceTest}, whose {@code @Test} methods each start
   * their own fresh container with its own {@code MODE}. One image build therefore serves all
   * four decoy modes, never one image per mode.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile dwEchoAndDecoyUpper() {
    return new ImageFromDockerfile()
        .withFileFromPath("echo.jar", path("eventbob.acceptance.dwEchoJar"))
        .withFileFromPath("decoy-upper.jar", path("eventbob.acceptance.decoyUpperJar"))
        .withDockerfileFromBuilder(builder -> builder
            .from("eclipse-temurin:21-jre")
            .copy("echo.jar", "/echo.jar")
            .copy("decoy-upper.jar", "/decoy-upper.jar")
            .env("PORT", String.valueOf(DW_UPPER_PORT))
            .entryPoint("sh", "-c",
                "java -jar /decoy-upper.jar & exec java -jar /echo.jar server"))
        .withBuildImageCmdModifier(cmd -> cmd.withLabels(CLEANUP_LABEL));
  }

  /**
   * Spring echo+decoy-upper image: runs the already-packaged {@code
   * io.eventbob.example.microlith.spring.echo} fat jar alongside this module's own non-EventBob
   * {@code io.eventbob.acceptance.decoy.upper} fat jar (instead of the real {@code spring.upper}
   * jar) as two OS processes sharing this one container's loopback interface, on the same {@link
   * #SPRING_UPPER_PORT} the Spring {@code EchoApplication}'s hardcoded remote capability URI
   * expects. The decoy's {@code PORT} is baked in at image-build time; its {@code MODE} is left
   * unset here and must be supplied per test via {@code GenericContainer#withEnv("MODE", ...)} -
   * see {@code RemoteCapabilityFailureAcceptanceTest}.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile springEchoAndDecoyUpper() {
    return new ImageFromDockerfile()
        .withFileFromPath("echo.jar", path("eventbob.acceptance.springEchoJar"))
        .withFileFromPath("decoy-upper.jar", path("eventbob.acceptance.decoyUpperJar"))
        .withDockerfileFromBuilder(builder -> builder
            .from("eclipse-temurin:21-jre")
            .copy("echo.jar", "/echo.jar")
            .copy("decoy-upper.jar", "/decoy-upper.jar")
            .env("PORT", String.valueOf(SPRING_UPPER_PORT))
            .entryPoint("sh", "-c",
                "java -jar /decoy-upper.jar & exec java -jar /echo.jar"))
        .withBuildImageCmdModifier(cmd -> cmd.withLabels(CLEANUP_LABEL));
  }

  /**
   * Broken Dropwizard image: registers a deliberately-broken set of inline lifecycles
   * (selected at container-start time by the {@code BROKEN_MODE} environment variable),
   * expected to abort startup, or fail cleanly on shutdown, via a real {@code EventBobBundle}
   * production code path.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile dwEchoBroken() {
    return jarImage(path("eventbob.acceptance.dwEchoBrokenJar"), "server");
  }

  /**
   * Broken Spring image: registers a deliberately-broken set of inline lifecycles (selected at
   * container-start time by the {@code BROKEN_MODE} environment variable), expected to abort
   * startup, or fail cleanly on shutdown, via a real {@code EventBobConfig} production code
   * path.
   *
   * @return a lazily-built image
   */
  public static ImageFromDockerfile springEchoBroken() {
    return jarImage(path("eventbob.acceptance.springEchoBrokenJar"));
  }

  private static Path path(String systemProperty) {
    String value = System.getProperty(systemProperty);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "System property '" + systemProperty + "' is not set; expected to be supplied by "
              + "maven-failsafe-plugin's systemPropertyVariables in io.eventbob.acceptance's "
              + "pom.xml");
    }
    return Path.of(value);
  }

  private static ImageFromDockerfile jarImage(Path jarFile, String... entryPointArgs) {
    String[] entryPoint = new String[entryPointArgs.length + 3];
    entryPoint[0] = "java";
    entryPoint[1] = "-jar";
    entryPoint[2] = "/app.jar";
    System.arraycopy(entryPointArgs, 0, entryPoint, 3, entryPointArgs.length);

    return new ImageFromDockerfile()
        .withFileFromPath("app.jar", jarFile)
        .withDockerfileFromBuilder(builder -> builder
            .from("eclipse-temurin:21-jre")
            .copy("app.jar", "/app.jar")
            .entryPoint(entryPoint))
        .withBuildImageCmdModifier(cmd -> cmd.withLabels(CLEANUP_LABEL));
  }
}
