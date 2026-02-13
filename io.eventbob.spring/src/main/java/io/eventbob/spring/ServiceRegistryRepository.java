package io.eventbob.spring;

import io.eventbob.core.endpointresolution.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Repository for service registry database operations.
 *
 * <p>Handles persistence of capabilities, macros, and their relationships.
 */
@Repository
public class ServiceRegistryRepository {
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistryRepository.class);

    private final JdbcTemplate jdbc;

    public ServiceRegistryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get current registry version for cache invalidation.
     */
    public long getCurrentVersion() {
        return jdbc.queryForObject(
            "SELECT version FROM registry_version WHERE id = 1",
            Long.class
        );
    }

    /**
     * Register a macro (logical service deployment unit).
     *
     * <p>Idempotent - updates endpoint if macro already registered.
     *
     * @return UUID of the macro (existing or newly created)
     */
    @Transactional
    public UUID registerMacro(
        String macroName,
        String endpoint) {

        UUID existingId = jdbc.query(
            "SELECT id FROM service_macros WHERE macro_name = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            macroName
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            // Update existing macro endpoint
            jdbc.update(
                """
                UPDATE service_macros
                SET endpoint = ?
                WHERE id = ?
                """,
                endpoint,
                existingId
            );

            log.debug("Updated macro: name={}, id={}", macroName, existingId);
            return existingId;
        } else {
            // Insert new macro
            UUID newId = jdbc.queryForObject(
                """
                INSERT INTO service_macros
                  (id, macro_name, endpoint)
                VALUES
                  (gen_random_uuid(), ?, ?)
                RETURNING id
                """,
                UUID.class,
                macroName,
                endpoint
            );

            log.info("Registered new macro: name={}, id={}", macroName, newId);
            return newId;
        }
    }

    /**
     * Register a capability (idempotent via ON CONFLICT).
     *
     * @return UUID of the capability (existing or newly created)
     */
    @Transactional
    public UUID registerCapability(CapabilityDescriptor descriptor) {

        // Check if capability already exists
        UUID existingId = jdbc.query(
            """
            SELECT id FROM service_capabilities
            WHERE service_name = ?
              AND capability = ?::capability_type
              AND capability_version = ?
              AND method = ?
              AND path_pattern = ?
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            descriptor.getServiceName(),
            descriptor.getCapability().name().toLowerCase(),
            descriptor.getCapabilityVersion(),
            descriptor.getMethod(),
            descriptor.getPathPattern()
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            log.debug("Capability already registered: {}, id={}", descriptor, existingId);
            return existingId;
        }

        // Insert new capability
        UUID newId = jdbc.queryForObject(
            """
            INSERT INTO service_capabilities
              (id, service_name, capability, capability_version, method, path_pattern)
            VALUES
              (gen_random_uuid(), ?, ?::capability_type, ?, ?, ?)
            RETURNING id
            """,
            UUID.class,
            descriptor.getServiceName(),
            descriptor.getCapability().name().toLowerCase(),
            descriptor.getCapabilityVersion(),
            descriptor.getMethod(),
            descriptor.getPathPattern()
        );

        log.info("Registered capability: {}, id={}", descriptor, newId);
        return newId;
    }

    /**
     * Link a macro to a capability (idempotent via ON CONFLICT).
     */
    @Transactional
    public void linkMacroCapability(UUID macroId, UUID capabilityId) {
        int inserted = jdbc.update(
            """
            INSERT INTO macro_capabilities (macro_id, capability_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """,
            macroId, capabilityId
        );

        if (inserted > 0) {
            log.debug("Linked macro {} to capability {}", macroId, capabilityId);
        }
    }
}
