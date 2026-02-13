package io.eventbob.spring;

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
        jdbc.execute("TRUNCATE service_capabilities, service_macros, macro_capabilities CASCADE");
        jdbc.execute("UPDATE registry_version SET version = 1");
    }

    @Test
    @DisplayName("Given capabilities, when registering macro, then capabilities and macro are persisted")
    void registerMacroWithCapabilities() {
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
            "messages-service",
            "messages-service",  // Endpoint = macro name
            capabilities
        );

        // Act
        CapabilityRegistrar.RegistrationResult result = registrar.registerMacro(request);

        // Assert
        assertTrue(result.isSuccess());
        assertNotNull(result.macroId());
        assertEquals(2, result.capabilityIds().size());

        // Verify macro in database
        UUID macroId = jdbc.queryForObject(
            "SELECT id FROM service_macros WHERE macro_name = 'messages-service'",
            UUID.class
        );
        assertEquals(result.macroId(), macroId);

        // Verify capabilities in database
        int capabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_capabilities WHERE service_name = 'messages'",
            Integer.class
        );
        assertEquals(2, capabilityCount);

        // Verify links
        int linkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM macro_capabilities WHERE macro_id = ?",
            Integer.class,
            macroId
        );
        assertEquals(2, linkCount);
    }

    @Test
    @DisplayName("Given different macros, when registering same capabilities, then capabilities are shared")
    void multipleMacrosSameCapabilities() {
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

        // Act - Register macro 1
        CapabilityRegistrar.RegistrationRequest request1 = new CapabilityRegistrar.RegistrationRequest(
            "messages-readonly",
            "messages-readonly",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult result1 = registrar.registerMacro(request1);

        // Act - Register macro 2 (same capabilities, different macro)
        CapabilityRegistrar.RegistrationRequest request2 = new CapabilityRegistrar.RegistrationRequest(
            "messages-replica",
            "messages-replica",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult result2 = registrar.registerMacro(request2);

        // Assert
        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertNotEquals(result1.macroId(), result2.macroId());

        // Only one capability record (shared by both macros)
        int capabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_capabilities WHERE service_name = 'messages'",
            Integer.class
        );
        assertEquals(1, capabilityCount);

        // Two macro records
        int macroCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_macros",
            Integer.class
        );
        assertEquals(2, macroCount);

        // Two links (one per macro)
        UUID capabilityId = result1.capabilityIds().values().iterator().next();
        int linkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM macro_capabilities WHERE capability_id = ?",
            Integer.class,
            capabilityId
        );
        assertEquals(2, linkCount);
    }

    @Test
    @DisplayName("Given existing macro, when re-registering, then macro endpoint is updated")
    void reRegisteringMacroUpdatesEndpoint() {
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
            "messages-service",
            "messages-service-old",
            capabilities
        );
        CapabilityRegistrar.RegistrationResult initialResult = registrar.registerMacro(initialRequest);

        // Act - Re-register with different endpoint
        CapabilityRegistrar.RegistrationRequest updateRequest = new CapabilityRegistrar.RegistrationRequest(
            "messages-service",
            "messages-service-new",  // Different endpoint
            capabilities
        );
        CapabilityRegistrar.RegistrationResult updateResult = registrar.registerMacro(updateRequest);

        // Assert
        assertEquals(initialResult.macroId(), updateResult.macroId());

        // Verify endpoint was updated
        String endpoint = jdbc.queryForObject(
            "SELECT endpoint FROM service_macros WHERE macro_name = 'messages-service'",
            String.class
        );
        assertEquals("messages-service-new", endpoint);

        // Still only one macro record
        int macroCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM service_macros WHERE macro_name = 'messages-service'",
            Integer.class
        );
        assertEquals(1, macroCount);
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
            "messages-service",
            "messages-service",
            capabilities
        );

        // Act
        registrar.registerMacro(request);

        // Assert
        long newVersion = repository.getCurrentVersion();
        assertTrue(newVersion > initialVersion, "Registry version should be bumped after registration");
    }
}
