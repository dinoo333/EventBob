package io.eventbob.registry;

import io.eventbob.core.endpointresolution.Capability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for capability registration with real database.
 */
@Disabled("Docker not available in current environment")
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CapabilityRegistrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("eventbob_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private CapabilityRegistrar registrar;

    @Autowired
    private ServiceRegistryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE service_capabilities, service_instances, instance_capabilities, deployment_history CASCADE");
        jdbc.execute("UPDATE registry_version SET version = 1");
    }

    @Test
    @DisplayName("Given capabilities, when registering instance, then capabilities and instance are persisted")
    void registerInstanceWithCapabilities() {
        // Arrange
        List<CapabilityDescriptor> capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .handlerClassName("com.example.GetContentHandler")
                .build(),
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.WRITE)
                .capabilityVersion(1)
                .method("POST")
                .pathPattern("/create")
                .handlerClassName("com.example.CreateMessageHandler")
                .build()
        );

        CapabilityRegistrar.RegistrationRequest request = new CapabilityRegistrar.RegistrationRequest(
            "messages-readonly-macro",
            "pod-1",
            "http://10.0.1.5:8080",
            1,
            DeploymentState.BLUE,
            "1.2.3",
            capabilities
        );

        // Act
        CapabilityRegistrar.RegistrationResult result = registrar.registerInstance(request);

        // Assert
        assertTrue(result.isSuccess());
        assertFalse(result.hasWarnings());
        assertNotNull(result.instanceId());
        assertEquals(2, result.capabilityIds().size());

        // Verify instance in database
        UUID instanceId = jdbc.queryForObject(
            "SELECT id FROM service_instances WHERE instance_id = 'pod-1'",
            UUID.class
        );
        assertEquals(result.instanceId(), instanceId);

        // Verify capabilities in database
        int capabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_capabilities WHERE service_name = 'messages'",
            Integer.class
        );
        assertEquals(2, capabilityCount);

        // Verify links
        int linkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM instance_capabilities WHERE instance_id = ?",
            Integer.class,
            instanceId
        );
        assertEquals(2, linkCount);

        // Verify deployment history
        int historyCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM deployment_history WHERE macro_name = 'messages-readonly-macro'",
            Integer.class
        );
        assertEquals(1, historyCount);
    }

    @Test
    @DisplayName("Given multiple instances, when registering same capabilities, then both instances are linked")
    void multipleInstancesSameCapabilities() {
        // Arrange
        List<CapabilityDescriptor> capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .handlerClassName("com.example.GetContentHandler")
                .build()
        );

        // Act - Register instance 1
        CapabilityRegistrar.RegistrationRequest request1 = new CapabilityRegistrar.RegistrationRequest(
            "messages-readonly-macro",
            "pod-1",
            "http://10.0.1.5:8080",
            1,
            DeploymentState.BLUE,
            "1.2.3",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult result1 = registrar.registerInstance(request1);

        // Act - Register instance 2 (same capabilities)
        CapabilityRegistrar.RegistrationRequest request2 = new CapabilityRegistrar.RegistrationRequest(
            "messages-readonly-macro",
            "pod-2",
            "http://10.0.1.6:8080",
            1,
            DeploymentState.BLUE,
            "1.2.3",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult result2 = registrar.registerInstance(request2);

        // Assert
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertNotEquals(result1.instanceId(), result2.instanceId());

        // Only one capability record (shared by both instances)
        int capabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_capabilities WHERE service_name = 'messages'",
            Integer.class
        );
        assertEquals(1, capabilityCount);

        // Two instance records
        int instanceCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_instances WHERE macro_name = 'messages-readonly-macro'",
            Integer.class
        );
        assertEquals(2, instanceCount);

        // Two links (one per instance)
        UUID capabilityId = result1.capabilityIds().values().iterator().next();
        int linkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM instance_capabilities WHERE capability_id = ?",
            Integer.class,
            capabilityId
        );
        assertEquals(2, linkCount);
    }

    @Test
    @DisplayName("Given capability version conflict, when registering, then warning is returned")
    void capabilityVersionConflict() {
        // Arrange - Register v1 capability
        List<CapabilityDescriptor> v1Capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .handlerClassName("com.example.GetContentHandler")
                .build()
        );

        CapabilityRegistrar.RegistrationRequest v1Request = new CapabilityRegistrar.RegistrationRequest(
            "messages-macro",
            "pod-1",
            "http://10.0.1.5:8080",
            1,
            DeploymentState.GREEN,
            "1.0.0",
            v1Capabilities
        );
        registrar.registerInstance(v1Request);

        // Act - Register v2 capability (conflicting version)
        List<CapabilityDescriptor> v2Capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(2)  // Different version!
                .method("GET")
                .pathPattern("/content")  // Same routing key
                .handlerClassName("com.example.GetContentHandlerV2")
                .build()
        );

        CapabilityRegistrar.RegistrationRequest v2Request = new CapabilityRegistrar.RegistrationRequest(
            "messages-macro",
            "pod-2",
            "http://10.0.1.6:8080",
            2,
            DeploymentState.BLUE,
            "2.0.0",
            v2Capabilities
        );
        CapabilityRegistrar.RegistrationResult result = registrar.registerInstance(v2Request);

        // Assert
        assertTrue(result.hasWarnings());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("version conflict"));
        assertTrue(result.warnings().get(0).contains("v1"));
        assertTrue(result.warnings().get(0).contains("v2"));

        // Conflicting capability was skipped
        assertEquals(0, result.capabilityIds().size());
    }

    @Test
    @DisplayName("Given existing instance, when re-registering, then instance is updated")
    void reRegisteringInstanceUpdates() {
        // Arrange - Initial registration
        List<CapabilityDescriptor> capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .handlerClassName("com.example.GetContentHandler")
                .build()
        );

        CapabilityRegistrar.RegistrationRequest initialRequest = new CapabilityRegistrar.RegistrationRequest(
            "messages-macro",
            "pod-1",
            "http://10.0.1.5:8080",
            1,
            DeploymentState.BLUE,
            "1.0.0",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult initialResult = registrar.registerInstance(initialRequest);

        // Act - Re-register with different endpoint
        CapabilityRegistrar.RegistrationRequest updateRequest = new CapabilityRegistrar.RegistrationRequest(
            "messages-macro",
            "pod-1",  // Same instance ID
            "http://10.0.1.10:8080",  // Different endpoint
            1,
            DeploymentState.BLUE,
            "1.0.0",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult updateResult = registrar.registerInstance(updateRequest);

        // Assert
        assertEquals(initialResult.instanceId(), updateResult.instanceId());

        // Verify endpoint was updated
        String endpoint = jdbc.queryForObject(
            "SELECT endpoint FROM service_instances WHERE instance_id = 'pod-1'",
            String.class
        );
        assertEquals("http://10.0.1.10:8080", endpoint);

        // Still only one instance record
        int instanceCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_instances WHERE macro_name = 'messages-macro'",
            Integer.class
        );
        assertEquals(1, instanceCount);
    }

    @Test
    @DisplayName("Given registry changes, when checking version, then version is bumped")
    void registryVersionBumping() {
        // Arrange
        long initialVersion = repository.getCurrentVersion();

        List<CapabilityDescriptor> capabilities = List.of(
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .handlerClassName("com.example.GetContentHandler")
                .build()
        );

        CapabilityRegistrar.RegistrationRequest request = new CapabilityRegistrar.RegistrationRequest(
            "messages-macro",
            "pod-1",
            "http://10.0.1.5:8080",
            1,
            DeploymentState.BLUE,
            "1.0.0",
            capabilities
        );

        // Act
        registrar.registerInstance(request);

        // Assert
        long newVersion = repository.getCurrentVersion();
        assertTrue(newVersion > initialVersion, "Registry version should be bumped after registration");
    }
}
