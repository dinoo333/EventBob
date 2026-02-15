package io.eventbob.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for registering capabilities and macroliths in the EventBob registry.
 *
 * <p>Handles idempotent registration - macroliths declare their capabilities.
 */
@Service
public class CapabilityRegistrar {
    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistrar.class);

    private final ServiceRegistryRepository repository;

    public CapabilityRegistrar(ServiceRegistryRepository repository) {
        this.repository = repository;
    }

    /**
     * Register a macrolith and its capabilities.
     *
     * <p>This operation is idempotent - re-registering updates the endpoint.
     *
     * @param request registration request
     * @return registration result
     */
    @Transactional
    public RegistrationResult registerMacrolith(RegistrationRequest request) {
        log.info("Registering macrolith: macrolith={}, capabilities={}",
            request.macrolithName(),
            request.capabilities().size());

        Map<String, UUID> capabilityIds = new HashMap<>();

        // Step 1: Register this macrolith
        UUID macrolithUuid = repository.registerMacrolith(
            request.macrolithName(),
            request.endpoint()
        );

        // Step 2: Register each capability and link to macrolith
        for (CapabilityDescriptor capability : request.capabilities()) {
            UUID capabilityId = repository.registerCapability(capability);

            capabilityIds.put(capability.getRoutingKey(), capabilityId);
            repository.linkMacrolithCapability(macrolithUuid, capabilityId);
        }

        log.info("Registration complete: macrolith={}, capabilities={}",
            request.macrolithName(),
            capabilityIds.size());

        return new RegistrationResult(macrolithUuid, capabilityIds);
    }


    /**
     * Registration request.
     */
    public record RegistrationRequest(
        String macrolithName,
        String endpoint,
        List<CapabilityDescriptor> capabilities
    ) {
        public RegistrationRequest {
            if (macrolithName == null || macrolithName.isBlank()) {
                throw new IllegalArgumentException("macrolithName is required");
            }
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalArgumentException("endpoint is required");
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
        UUID macrolithId,
        Map<String, UUID> capabilityIds
    ) {
        public boolean isSuccess() {
            return macrolithId != null && !capabilityIds.isEmpty();
        }
    }
}
