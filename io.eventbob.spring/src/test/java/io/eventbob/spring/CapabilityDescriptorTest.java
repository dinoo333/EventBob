package io.eventbob.spring;

import io.eventbob.core.endpointresolution.Capability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityDescriptorTest {

    @Test
    void shouldBuildValidDescriptor() {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.READ)
            .capabilityVersion(1)
            .method("GET")
            .pathPattern("/content")
            .build();

        assertEquals("messages", descriptor.getServiceName());
        assertEquals(Capability.READ, descriptor.getCapability());
        assertEquals(1, descriptor.getCapabilityVersion());
        assertEquals("GET", descriptor.getMethod());
        assertEquals("/content", descriptor.getPathPattern());
    }

    @Test
    void shouldGenerateRoutingKey() {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.WRITE)
            .capabilityVersion(2)
            .method("POST")
            .pathPattern("/content/{id}")
            .build();

        assertEquals("messages:WRITE:POST:/content/{id}", descriptor.getRoutingKey());
    }

    @Test
    void shouldRejectNullServiceName() {
        assertThrows(NullPointerException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName(null)
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .build()
        );
    }

    @Test
    void shouldRejectNullCapability() {
        assertThrows(NullPointerException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(null)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern("/content")
                .build()
        );
    }

    @Test
    void shouldRejectNullMethod() {
        assertThrows(NullPointerException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method(null)
                .pathPattern("/content")
                .build()
        );
    }

    @Test
    void shouldRejectNullPathPattern() {
        assertThrows(NullPointerException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(1)
                .method("GET")
                .pathPattern(null)
                .build()
        );
    }

    @Test
    void shouldRejectNonPositiveVersion() {
        assertThrows(IllegalArgumentException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(0)
                .method("GET")
                .pathPattern("/content")
                .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
            CapabilityDescriptor.builder()
                .serviceName("messages")
                .capability(Capability.READ)
                .capabilityVersion(-1)
                .method("GET")
                .pathPattern("/content")
                .build()
        );
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        CapabilityDescriptor d1 = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.READ)
            .capabilityVersion(1)
            .method("GET")
            .pathPattern("/content")
            .build();

        CapabilityDescriptor d2 = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.READ)
            .capabilityVersion(1)
            .method("GET")
            .pathPattern("/content")
            .build();

        // Descriptors with same routing key are equal
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void shouldNotEqualDifferentRoutingKey() {
        CapabilityDescriptor d1 = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.READ)
            .capabilityVersion(1)
            .method("GET")
            .pathPattern("/content")
            .build();

        CapabilityDescriptor d2 = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.READ)
            .capabilityVersion(2)  // Different version
            .method("GET")
            .pathPattern("/content")
            .build();

        assertNotEquals(d1, d2);
    }

    @Test
    void shouldHaveReadableToString() {
        CapabilityDescriptor descriptor = CapabilityDescriptor.builder()
            .serviceName("messages")
            .capability(Capability.WRITE)
            .capabilityVersion(3)
            .method("POST")
            .pathPattern("/bulk-content")
            .build();

        String toString = descriptor.toString();
        assertTrue(toString.contains("messages"));
        assertTrue(toString.contains("WRITE"));
        assertTrue(toString.contains("POST"));
        assertTrue(toString.contains("/bulk-content"));
    }
}
