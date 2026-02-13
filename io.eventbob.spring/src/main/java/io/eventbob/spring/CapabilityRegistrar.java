package io.eventbob.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for registering capabilities and instances in the EventBob registry.
 *
 * <p>Handles:
 * <ul>
 *   <li>Idempotent registration (multiple instances can register same capabilities)</li>
 *   <li>Conflict detection (capability version mismatches)</li>
 *   <li>Audit logging (deployment events)</li>
 * </ul>
 */
@Service
public class CapabilityRegistrar {
    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistrar.class);

    private final ServiceRegistryRepository repository;

    public CapabilityRegistrar(ServiceRegistryRepository repository) {
        this.repository = repository;
    }

    /**
     * Register an instance and its capabilities.
     *
     * <p>This operation is idempotent - multiple instances of the same macro
     * can call this simultaneously, and the result will be consistent.
     *
     * @param request registration request
     * @return registration result with any warnings
     */
    @Transactional
    public RegistrationResult registerInstance(RegistrationRequest request) {
        log.info("Registering instance: macro={}, instance={}, deployment=v{}, capabilities={}",
            request.macroName(),
            request.instanceId(),
            request.deploymentVersion(),
            request.capabilities().size());

        List<String> warnings = new ArrayList<>();
        Map<String, UUID> capabilityIds = new HashMap<>();

        // Step 1: Register this instance
        UUID instanceUuid = repository.registerInstance(
            request.macroName(),
            request.instanceId(),
            request.endpoint(),
            request.deploymentVersion()
        );

        // Step 2: For each capability, check for conflicts and register
        for (CapabilityDescriptor capability : request.capabilities()) {
            try {
                UUID capabilityId = registerCapabilityWithConflictCheck(
                    capability,
                    request.deploymentVersion(),
                    request.state(),
                    request.jarVersion(),
                    warnings
                );

                if (capabilityId != null) {
                    capabilityIds.put(capability.getRoutingKey(), capabilityId);

                    // Link instance to capability
                    repository.linkInstanceCapability(instanceUuid, capabilityId);
                }

            } catch (Exception e) {
                log.error("Failed to register capability: {}", capability, e);
                warnings.add(String.format(
                    "Failed to register %s: %s", capability, e.getMessage()
                ));
            }
        }

        // Step 3: Record deployment event
        if (!request.capabilities().isEmpty()) {
            String serviceName = request.capabilities().get(0).getServiceName();
            repository.recordDeploymentEvent(
                serviceName,
                request.macroName(),
                request.deploymentVersion(),
                "deployed",
                null,
                request.state(),
                request.instanceId(),
                String.format("Registered %d capabilities", request.capabilities().size())
            );
        }

        log.info("Registration complete: instance={}, capabilities={}, warnings={}",
            request.instanceId(),
            capabilityIds.size(),
            warnings.size());

        return new RegistrationResult(instanceUuid, capabilityIds, warnings);
    }

    /**
     * Register a single capability with conflict detection.
     *
     * @return capability UUID if registered, null if skipped due to conflict
     */
    private UUID registerCapabilityWithConflictCheck(
        CapabilityDescriptor capability,
        int deploymentVersion,
        DeploymentState state,
        String jarVersion,
        List<String> warnings) {

        // Check for existing capabilities with same routing key
        List<ServiceRegistryRepository.CapabilityRecord> existing =
            repository.findAllCapabilitiesByRoutingKey(
                capability.getServiceName(),
                capability.getCapability(),
                capability.getMethod(),
                capability.getPathPattern()
            );

        // Detect version conflicts
        for (ServiceRegistryRepository.CapabilityRecord existingCap : existing) {
            if (existingCap.capabilityVersion() != capability.getCapabilityVersion()) {
                String warning = String.format(
                    "Capability version conflict: %s has v%d in deployment %d (%s), " +
                        "but v%d in deployment %d (%s). Skipping registration.",
                    capability.getRoutingKey(),
                    existingCap.capabilityVersion(),
                    existingCap.deploymentVersion(),
                    existingCap.state(),
                    capability.getCapabilityVersion(),
                    deploymentVersion,
                    state
                );

                log.warn(warning);
                warnings.add(warning);

                // TODO: Emit metric for conflict detection
                return null;  // Skip conflicting capability
            }
        }

        // No conflicts - register capability
        return repository.registerCapability(capability, deploymentVersion, state, jarVersion);
    }

    /**
     * Detect all capability conflicts in the registry.
     *
     * <p>This can be called periodically to validate registry consistency.
     */
    public List<ServiceRegistryRepository.CapabilityConflict> detectAllConflicts() {
        return repository.detectConflicts();
    }

    /**
     * Registration request.
     */
    public record RegistrationRequest(
        String macroName,
        String instanceId,
        String endpoint,
        int deploymentVersion,
        DeploymentState state,
        String jarVersion,
        List<CapabilityDescriptor> capabilities
    ) {
        public RegistrationRequest {
            if (macroName == null || macroName.isBlank()) {
                throw new IllegalArgumentException("macroName is required");
            }
            if (instanceId == null || instanceId.isBlank()) {
                throw new IllegalArgumentException("instanceId is required");
            }
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalArgumentException("endpoint is required");
            }
            if (deploymentVersion <= 0) {
                throw new IllegalArgumentException("deploymentVersion must be positive");
            }
            if (state == null) {
                throw new IllegalArgumentException("state is required");
            }
            if (jarVersion == null || jarVersion.isBlank()) {
                throw new IllegalArgumentException("jarVersion is required");
            }
            if (capabilities == null) {
                throw new IllegalArgumentException("capabilities is required");
            }
        }
    }

    /**
     * Registration result.
     */
    public record RegistrationResult(
        UUID instanceId,
        Map<String, UUID> capabilityIds,
        List<String> warnings
    ) {
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isSuccess() {
            return instanceId != null && !capabilityIds.isEmpty();
        }
    }
}
