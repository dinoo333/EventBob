package io.eventbob.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Bootstraps a macro-service by:
 * <ol>
 *   <li>Scanning JARs for capabilities</li>
 *   <li>Registering capabilities and macro in the registry</li>
 * </ol>
 */
@Component
public class MacroServiceBootstrap {
    private static final Logger log = LoggerFactory.getLogger(MacroServiceBootstrap.class);

    private final CapabilityScanner scanner;
    private final CapabilityRegistrar registrar;

    public MacroServiceBootstrap(
        CapabilityScanner scanner,
        CapabilityRegistrar registrar) {

        this.scanner = scanner;
        this.registrar = registrar;
    }

    /**
     * Bootstrap a macro-service.
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

            // Step 2: Determine macro endpoint (default to macro name)
            String endpoint = config.endpoint() != null
                ? config.endpoint()
                : config.macroName();

            log.info("Macro endpoint: {}", endpoint);

            // Step 3: Register macro and capabilities
            CapabilityRegistrar.RegistrationRequest request = new CapabilityRegistrar.RegistrationRequest(
                config.macroName(),
                endpoint,
                allCapabilities
            );

            CapabilityRegistrar.RegistrationResult registrationResult = registrar.registerMacro(request);

            if (!registrationResult.isSuccess()) {
                throw new BootstrapException("Registration failed: no capabilities registered");
            }

            log.info("Bootstrap complete: macro={}, capabilities={}",
                config.macroName(),
                registrationResult.capabilityIds().size());

            return new BootstrapResult(
                registrationResult.macroId(),
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
     * Bootstrap configuration.
     */
    public record BootstrapConfig(
        String macroName,
        List<Path> jarPaths,
        String endpoint  // null = defaults to macroName
    ) {
        public BootstrapConfig {
            if (macroName == null || macroName.isBlank()) {
                throw new IllegalArgumentException("macroName is required");
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
        UUID macroId,
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
