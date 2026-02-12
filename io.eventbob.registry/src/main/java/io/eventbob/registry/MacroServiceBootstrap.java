package io.eventbob.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Bootstraps a macro-service instance by:
 * <ol>
 *   <li>Scanning JARs for capabilities</li>
 *   <li>Registering capabilities and instance in the registry</li>
 *   <li>Starting the event router</li>
 * </ol>
 */
@Component
public class MacroServiceBootstrap {
    private static final Logger log = LoggerFactory.getLogger(MacroServiceBootstrap.class);

    private final CapabilityScanner scanner;
    private final CapabilityRegistrar registrar;
    private final ServiceRegistryRepository repository;

    public MacroServiceBootstrap(
        CapabilityScanner scanner,
        CapabilityRegistrar registrar,
        ServiceRegistryRepository repository) {

        this.scanner = scanner;
        this.registrar = registrar;
        this.repository = repository;
    }

    /**
     * Bootstrap a macro-service instance.
     *
     * @param config bootstrap configuration
     * @return bootstrap result
     * @throws BootstrapException if bootstrap fails
     */
    public BootstrapResult bootstrap(BootstrapConfig config) throws BootstrapException {
        log.info("Bootstrapping macro-service: {}", config.macroName());

        try {
            // Step 1: Scan JARs for capabilities
            log.info("Scanning {} JARs for capabilities", config.jarPaths().size());
            List<CapabilityDescriptor> allCapabilities = scanJars(config);
            log.info("Found {} total capabilities across all JARs", allCapabilities.size());

            if (allCapabilities.isEmpty()) {
                throw new BootstrapException("No capabilities found in JARs");
            }

            // Step 2: Determine deployment version
            int deploymentVersion = config.deploymentVersion() != null
                ? config.deploymentVersion()
                : repository.getNextDeploymentVersion(config.macroName());

            log.info("Using deployment version: {}", deploymentVersion);

            // Step 3: Determine instance endpoint
            String endpoint = config.endpoint() != null
                ? config.endpoint()
                : buildDefaultEndpoint(config.port());

            log.info("Instance endpoint: {}", endpoint);

            // Step 4: Register instance and capabilities
            CapabilityRegistrar.RegistrationRequest request = new CapabilityRegistrar.RegistrationRequest(
                config.macroName(),
                config.instanceId(),
                endpoint,
                deploymentVersion,
                config.initialState(),
                config.jarVersion(),
                allCapabilities
            );

            CapabilityRegistrar.RegistrationResult registrationResult = registrar.registerInstance(request);

            if (!registrationResult.isSuccess()) {
                throw new BootstrapException("Registration failed: no capabilities registered");
            }

            // Log warnings if any
            if (registrationResult.hasWarnings()) {
                log.warn("Registration completed with {} warnings:", registrationResult.warnings().size());
                registrationResult.warnings().forEach(warning -> log.warn("  - {}", warning));
            }

            log.info("Bootstrap complete: instance={}, capabilities={}",
                registrationResult.instanceId(),
                registrationResult.capabilityIds().size());

            return new BootstrapResult(
                registrationResult.instanceId(),
                deploymentVersion,
                endpoint,
                allCapabilities,
                registrationResult.warnings()
            );

        } catch (BootstrapException e) {
            throw e;
        } catch (Exception e) {
            throw new BootstrapException("Bootstrap failed: " + e.getMessage(), e);
        }
    }

    /**
     * Scan all JARs and collect capabilities.
     */
    private List<CapabilityDescriptor> scanJars(BootstrapConfig config) throws Exception {
        ClassLoader parentClassLoader = getClass().getClassLoader();

        return config.jarPaths().stream()
            .flatMap(jarPath -> {
                try {
                    return scanner.scanJar(jarPath, parentClassLoader).stream();
                } catch (Exception e) {
                    log.error("Failed to scan JAR: {}", jarPath, e);
                    return java.util.stream.Stream.empty();
                }
            })
            .toList();
    }

    /**
     * Build default endpoint from local hostname and port.
     */
    private String buildDefaultEndpoint(int port) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return String.format("http://%s:%d", hostname, port);
        } catch (Exception e) {
            log.warn("Failed to determine hostname, using localhost", e);
            return String.format("http://localhost:%d", port);
        }
    }

    /**
     * Bootstrap configuration.
     */
    public record BootstrapConfig(
        String macroName,
        String instanceId,
        List<Path> jarPaths,
        String jarVersion,
        DeploymentState initialState,
        Integer deploymentVersion,  // null = auto-increment
        String endpoint,            // null = auto-detect
        int port
    ) {
        public BootstrapConfig {
            if (macroName == null || macroName.isBlank()) {
                throw new IllegalArgumentException("macroName is required");
            }
            if (instanceId == null || instanceId.isBlank()) {
                throw new IllegalArgumentException("instanceId is required");
            }
            if (jarPaths == null || jarPaths.isEmpty()) {
                throw new IllegalArgumentException("jarPaths is required");
            }
            if (jarVersion == null || jarVersion.isBlank()) {
                throw new IllegalArgumentException("jarVersion is required");
            }
            if (initialState == null) {
                throw new IllegalArgumentException("initialState is required");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
        }
    }

    /**
     * Bootstrap result.
     */
    public record BootstrapResult(
        UUID instanceId,
        int deploymentVersion,
        String endpoint,
        List<CapabilityDescriptor> capabilities,
        List<String> warnings
    ) {}

    /**
     * Bootstrap exception.
     */
    public static class BootstrapException extends Exception {
        public BootstrapException(String message) {
            super(message);
        }

        public BootstrapException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
