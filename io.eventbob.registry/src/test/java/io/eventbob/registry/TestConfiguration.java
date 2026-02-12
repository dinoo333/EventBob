package io.eventbob.registry;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Test configuration for registry integration tests.
 *
 * <p>This configuration enables Spring Boot test context without requiring
 * a full application class in the registry module.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public class TestConfiguration {
}
