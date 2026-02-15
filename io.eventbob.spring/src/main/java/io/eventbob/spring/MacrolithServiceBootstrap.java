package io.eventbob.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bootstraps a macrolith-service by:
 * <ol>
 *   <li>Scanning JARs for capabilities</li>
 *   <li>Registering capabilities and macrolith in the registry</li>
 * </ol>
 */
@Component
public class MacrolithServiceBootstrap {
    private static final Logger log = LoggerFactory.getLogger(MacrolithServiceBootstrap.class);

    private final CapabilityScanner scanner;
    private final CapabilityRegistrar registrar;

    public MacrolithServiceBootstrap(
        CapabilityScanner scanner,
        CapabilityRegistrar registrar) {

        this.scanner = scanner;
        this.registrar = registrar;
    }

    /**
     * Bootstrap a macrolith-service.
     *
     * @param config bootstrap configuration
     * @return bootstrap result
     * @throws BootstrapException if bootstrap fails
     */
    public BootstrapResult bootstrap(BootstrapConfig config) throws BootstrapException {
        log.info("Bootstrapping macrolith-service: {}", config.macrolithName());

        try {
            // Step 1: Scan JARs for capabilities
            log.info("Scanning {} JARs for capabilities", config.jarPaths().size());
            List<CapabilityDescriptor> allCapabilities = scanJars(config);
            log.info("Found {} total capabilities across all JARs", allCapabilities.size());

            if (allCapabilities.isEmpty()) {
                throw new BootstrapException("No capabilities found in JARs");
            }

            // Step 2: Determine macrolith endpoint (default to macrolith name)
            String endpoint = config.endpoint() != null
                ? config.endpoint()
                : config.macrolithName();

            log.info("Macrolith endpoint: {}", endpoint);

            // Step 3: Register macrolith and capabilities
            CapabilityRegistrar.RegistrationRequest request = new CapabilityRegistrar.RegistrationRequest(
                config.macrolithName(),
                endpoint,
                allCapabilities
            );

            CapabilityRegistrar.RegistrationResult registrationResult = registrar.registerMacrolith(request);

            if (!registrationResult.isSuccess()) {
                throw new BootstrapException("Registration failed: no capabilities registered");
            }

            log.info("Bootstrap complete: macrolith={}, capabilities={}",
                config.macrolithName(),
                registrationResult.capabilityIds().size());

            return new BootstrapResult(
                registrationResult.macrolithId(),
                endpoint,
                allCapabilities
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
        List<CapabilityDescriptor> allCapabilities = new ArrayList<>();

        for (Path jarPath : config.jarPaths()) {
            List<CapabilityDescriptor> jarCapabilities = scanner.scanJar(jarPath, parentClassLoader);
            allCapabilities.addAll(jarCapabilities);
        }

        return allCapabilities;
    }


    /**
     * Bootstrap configuration.
     */
    public record BootstrapConfig(
        String macrolithName,
        List<Path> jarPaths,
        String endpoint  // null = defaults to macrolithName
    ) {
        public BootstrapConfig {
            if (macrolithName == null || macrolithName.isBlank()) {
                throw new IllegalArgumentException("macrolithName is required");
            }
            if (jarPaths == null || jarPaths.isEmpty()) {
                throw new IllegalArgumentException("jarPaths is required");
            }
        }
    }

    /**
     * Bootstrap result.
     */
    public record BootstrapResult(
        UUID macrolithId,
        String endpoint,
        List<CapabilityDescriptor> capabilities
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
