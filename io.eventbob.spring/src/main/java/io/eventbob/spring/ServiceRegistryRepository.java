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
 * <p>Handles persistence of capabilities, macroliths, and their relationships.
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
     * Register a macrolith (logical service deployment unit).
     *
     * <p>Idempotent - updates endpoint if macrolith already registered.
     *
     * @return UUID of the macrolith (existing or newly created)
     */
    @Transactional
    public UUID registerMacrolith(
        String macrolithName,
        String endpoint) {

        UUID existingId = jdbc.query(
            "SELECT id FROM service_macroliths WHERE macrolith_name = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            macrolithName
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            // Update existing macrolith endpoint
            jdbc.update(
                """
                UPDATE service_macroliths
                SET endpoint = ?
                WHERE id = ?
                """,
                endpoint,
                existingId
            );

            log.debug("Updated macrolith: name={}, id={}", macrolithName, existingId);
            return existingId;
        } else {
            // Insert new macrolith
            UUID newId = jdbc.queryForObject(
                """
                INSERT INTO service_macroliths
                  (id, macrolith_name, endpoint)
                VALUES
                  (gen_random_uuid(), ?, ?)
                RETURNING id
                """,
                UUID.class,
                macrolithName,
                endpoint
            );

            log.info("Registered new macrolith: name={}, id={}", macrolithName, newId);
            return newId;
        }
    }

    /**
     * Register a capability (idempotent via ON CONFLICT DO NOTHING).
     *
     * @return UUID of the capability (existing or newly created)
     */
    @Transactional
    public UUID registerCapability(CapabilityDescriptor descriptor) {

        // Insert new capability or return existing one
        // ON CONFLICT DO NOTHING handles duplicate registrations gracefully
        UUID capabilityId = jdbc.queryForObject(
            """
            INSERT INTO service_capabilities
              (id, service_name, capability, capability_version, method, path_pattern)
            VALUES
              (gen_random_uuid(), ?, ?::capability_type, ?, ?, ?)
            ON CONFLICT (service_name, capability, capability_version, method, path_pattern)
            DO NOTHING
            RETURNING id
            """,
            UUID.class,
            descriptor.getServiceName(),
            descriptor.getCapability().name().toLowerCase(),
            descriptor.getCapabilityVersion(),
            descriptor.getMethod(),
            descriptor.getPathPattern()
        );

        // If ON CONFLICT fired, RETURNING clause returns nothing, so we need to query
        if (capabilityId == null) {
            capabilityId = jdbc.queryForObject(
                """
                SELECT id FROM service_capabilities
                WHERE service_name = ?
                  AND capability = ?::capability_type
                  AND capability_version = ?
                  AND method = ?
                  AND path_pattern = ?
                """,
                UUID.class,
                descriptor.getServiceName(),
                descriptor.getCapability().name().toLowerCase(),
                descriptor.getCapabilityVersion(),
                descriptor.getMethod(),
                descriptor.getPathPattern()
            );
            log.debug("Capability already registered: {}, id={}", descriptor, capabilityId);
        } else {
            log.info("Registered new capability: {}, id={}", descriptor, capabilityId);
        }

        return capabilityId;
    }

    /**
     * Link a macrolith to a capability (idempotent via ON CONFLICT).
     */
    @Transactional
    public void linkMacrolithCapability(UUID macrolithId, UUID capabilityId) {
        int inserted = jdbc.update(
            """
            INSERT INTO macrolith_capabilities (macrolith_id, capability_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """,
            macrolithId, capabilityId
        );

        if (inserted > 0) {
            log.debug("Linked macrolith {} to capability {}", macrolithId, capabilityId);
        }
    }
}
