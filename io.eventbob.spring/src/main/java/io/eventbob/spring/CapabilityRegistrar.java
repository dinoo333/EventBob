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
 * Service for registering capabilities and macros in the EventBob registry.
 *
 * <p>Handles idempotent registration - macros declare their capabilities.
 */
@Service
public class CapabilityRegistrar {
    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistrar.class);

    private final ServiceRegistryRepository repository;

    public CapabilityRegistrar(ServiceRegistryRepository repository) {
        this.repository = repository;
    }

    /**
     * Register a macro and its capabilities.
     *
     * <p>This operation is idempotent - re-registering updates the endpoint.
     *
     * @param request registration request
     * @return registration result
     */
    @Transactional
    public RegistrationResult registerMacro(RegistrationRequest request) {
        log.info("Registering macro: macro={}, capabilities={}",
            request.macroName(),
            request.capabilities().size());

        Map<String, UUID> capabilityIds = new HashMap<>();

        // Step 1: Register this macro
        UUID macroUuid = repository.registerMacro(
            request.macroName(),
            request.endpoint()
        );

        // Step 2: Register each capability and link to macro
        for (CapabilityDescriptor capability : request.capabilities()) {
            UUID capabilityId = repository.registerCapability(capability);

            capabilityIds.put(capability.getRoutingKey(), capabilityId);
            repository.linkMacroCapability(macroUuid, capabilityId);
        }

        log.info("Registration complete: macro={}, capabilities={}",
            request.macroName(),
            capabilityIds.size());

        return new RegistrationResult(macroUuid, capabilityIds);
    }


    /**
     * Registration request.
     */
    public record RegistrationRequest(
        String macroName,
        String endpoint,
        List<CapabilityDescriptor> capabilities
    ) {
        public RegistrationRequest {
            if (macroName == null || macroName.isBlank()) {
                throw new IllegalArgumentException("macroName is required");
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
        UUID macroId,
        Map<String, UUID> capabilityIds
    ) {
        public boolean isSuccess() {
            return macroId != null && !capabilityIds.isEmpty();
        }
    }
}
