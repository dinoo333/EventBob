package io.eventbob.spring;

import io.eventbob.core.endpointresolution.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for service registry database operations.
 *
 * <p>Handles persistence of capabilities, instances, and their relationships.
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
     * Get next deployment version for a macro (monotonically increasing).
     */
    public int getNextDeploymentVersion(String macroName) {
        Integer maxVersion = jdbc.queryForObject(
            """
            SELECT MAX(deployment_version)
            FROM service_instances
            WHERE macro_name = ?
            """,
            Integer.class,
            macroName
        );

        return (maxVersion != null) ? maxVersion + 1 : 1;
    }

    /**
     * Register a service instance (idempotent via ON CONFLICT).
     *
     * @return UUID of the instance (existing or newly created)
     */
    @Transactional
    public UUID registerInstance(
        String macroName,
        String instanceId,
        String endpoint,
        int deploymentVersion) {

        UUID existingId = jdbc.query(
            "SELECT id FROM service_instances WHERE macro_name = ? AND instance_id = ?",
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            macroName, instanceId
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            // Update existing instance
            jdbc.update(
                """
                UPDATE service_instances
                SET endpoint = ?,
                    deployment_version = ?,
                    status = ?::instance_status,
                    last_heartbeat = NOW()
                WHERE id = ?
                """,
                endpoint,
                deploymentVersion,
                InstanceStatus.HEALTHY.name().toLowerCase(),
                existingId
            );

            log.debug("Updated instance: macro={}, instance={}, id={}",
                macroName, instanceId, existingId);
            return existingId;
        } else {
            // Insert new instance
            UUID newId = jdbc.queryForObject(
                """
                INSERT INTO service_instances
                  (id, macro_name, instance_id, endpoint, deployment_version, status)
                VALUES
                  (gen_random_uuid(), ?, ?, ?, ?, ?::instance_status)
                RETURNING id
                """,
                UUID.class,
                macroName,
                instanceId,
                endpoint,
                deploymentVersion,
                InstanceStatus.HEALTHY.name().toLowerCase()
            );

            log.info("Registered new instance: macro={}, instance={}, id={}",
                macroName, instanceId, newId);
            return newId;
        }
    }

    /**
     * Register a capability (idempotent via ON CONFLICT).
     *
     * @return UUID of the capability (existing or newly created)
     */
    @Transactional
    public UUID registerCapability(
        CapabilityDescriptor descriptor,
        int deploymentVersion,
        DeploymentState state,
        String jarVersion) {

        // Check if capability already exists
        UUID existingId = jdbc.query(
            """
            SELECT id FROM service_capabilities
            WHERE service_name = ?
              AND capability = ?::capability_type
              AND capability_version = ?
              AND method = ?
              AND path_pattern = ?
              AND deployment_version = ?
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            descriptor.getServiceName(),
            descriptor.getCapability().name().toLowerCase(),
            descriptor.getCapabilityVersion(),
            descriptor.getMethod(),
            descriptor.getPathPattern(),
            deploymentVersion
        ).stream().findFirst().orElse(null);

        if (existingId != null) {
            log.debug("Capability already registered: {}, id={}", descriptor, existingId);
            return existingId;
        }

        // Insert new capability
        UUID newId = jdbc.queryForObject(
            """
            INSERT INTO service_capabilities
              (id, service_name, capability, capability_version, method, path_pattern,
               deployment_version, deployment_state, jar_version, became_blue_at)
            VALUES
              (gen_random_uuid(), ?, ?::capability_type, ?, ?, ?,
               ?, ?::deployment_state, ?,
               CASE WHEN ?::deployment_state = 'blue' THEN NOW() ELSE NULL END)
            RETURNING id
            """,
            UUID.class,
            descriptor.getServiceName(),
            descriptor.getCapability().name().toLowerCase(),
            descriptor.getCapabilityVersion(),
            descriptor.getMethod(),
            descriptor.getPathPattern(),
            deploymentVersion,
            state.name().toLowerCase(),
            jarVersion,
            state.name().toLowerCase()
        );

        log.info("Registered capability: {} as {}, id={}", descriptor, state, newId);
        return newId;
    }

    /**
     * Link an instance to a capability (idempotent via ON CONFLICT).
     */
    @Transactional
    public void linkInstanceCapability(UUID instanceId, UUID capabilityId) {
        int inserted = jdbc.update(
            """
            INSERT INTO instance_capabilities (instance_id, capability_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """,
            instanceId, capabilityId
        );

        if (inserted > 0) {
            log.debug("Linked instance {} to capability {}", instanceId, capabilityId);
        }
    }

    /**
     * Find existing capability by routing key and deployment version.
     *
     * @return capability record or null if not found
     */
    public CapabilityRecord findCapability(
        String serviceName,
        Capability capability,
        String method,
        String pathPattern,
        int deploymentVersion) {

        List<CapabilityRecord> results = jdbc.query(
            """
            SELECT id, service_name, capability, capability_version, method, path_pattern,
                   deployment_version, deployment_state, jar_version
            FROM service_capabilities
            WHERE service_name = ?
              AND capability = ?::capability_type
              AND method = ?
              AND path_pattern = ?
              AND deployment_version = ?
            """,
            this::mapCapabilityRecord,
            serviceName,
            capability.name().toLowerCase(),
            method,
            pathPattern,
            deploymentVersion
        );

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Find all capabilities matching routing key (across all deployment versions).
     */
    public List<CapabilityRecord> findAllCapabilitiesByRoutingKey(
        String serviceName,
        Capability capability,
        String method,
        String pathPattern) {

        return jdbc.query(
            """
            SELECT id, service_name, capability, capability_version, method, path_pattern,
                   deployment_version, deployment_state, jar_version
            FROM service_capabilities
            WHERE service_name = ?
              AND capability = ?::capability_type
              AND method = ?
              AND path_pattern = ?
              AND deployment_state IN ('green', 'blue')
            ORDER BY deployment_version DESC
            """,
            this::mapCapabilityRecord,
            serviceName,
            capability.name().toLowerCase(),
            method,
            pathPattern
        );
    }

    /**
     * Detect capability version conflicts (same routing key, different versions, both active).
     */
    public List<CapabilityConflict> detectConflicts() {
        return jdbc.query(
            """
            SELECT * FROM capability_conflicts
            """,
            this::mapCapabilityConflict
        );
    }

    /**
     * Record deployment event in audit log.
     */
    @Transactional
    public void recordDeploymentEvent(
        String serviceName,
        String macroName,
        int deploymentVersion,
        String eventType,
        DeploymentState fromState,
        DeploymentState toState,
        String triggeredBy,
        String reason) {

        jdbc.update(
            """
            INSERT INTO deployment_history
              (service_name, macro_name, deployment_version, event_type,
               from_state, to_state, triggered_by, reason)
            VALUES (?, ?, ?, ?, ?::deployment_state, ?::deployment_state, ?, ?)
            """,
            serviceName,
            macroName,
            deploymentVersion,
            eventType,
            fromState != null ? fromState.name().toLowerCase() : null,
            toState != null ? toState.name().toLowerCase() : null,
            triggeredBy,
            reason
        );
    }

    // Row mappers

    private CapabilityRecord mapCapabilityRecord(ResultSet rs, int rowNum) throws SQLException {
        return new CapabilityRecord(
            (UUID) rs.getObject("id"),
            rs.getString("service_name"),
            Capability.valueOf(rs.getString("capability").toUpperCase()),
            rs.getInt("capability_version"),
            rs.getString("method"),
            rs.getString("path_pattern"),
            rs.getInt("deployment_version"),
            DeploymentState.valueOf(rs.getString("deployment_state").toUpperCase()),
            rs.getString("jar_version")
        );
    }

    private CapabilityConflict mapCapabilityConflict(ResultSet rs, int rowNum) throws SQLException {
        return new CapabilityConflict(
            rs.getString("service_name"),
            Capability.valueOf(rs.getString("capability").toUpperCase()),
            rs.getString("method"),
            rs.getString("path_pattern"),
            rs.getInt("version_1"),
            rs.getInt("version_2"),
            rs.getInt("deployment_1"),
            rs.getInt("deployment_2")
        );
    }

    /**
     * Record returned from database queries.
     */
    public record CapabilityRecord(
        UUID id,
        String serviceName,
        Capability capability,
        int capabilityVersion,
        String method,
        String pathPattern,
        int deploymentVersion,
        DeploymentState state,
        String jarVersion
    ) {}

    /**
     * Conflict detected between capability versions.
     */
    public record CapabilityConflict(
        String serviceName,
        Capability capability,
        String method,
        String pathPattern,
        int version1,
        int version2,
        int deployment1,
        int deployment2
    ) {
        public String getRoutingKey() {
            return String.format("%s:%s:%s:%s", serviceName, capability, method, pathPattern);
        }
    }
}
