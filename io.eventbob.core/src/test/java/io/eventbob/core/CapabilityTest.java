package io.eventbob.core;

import io.eventbob.core.endpointresolution.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CapabilityTest {

    @Test
    @DisplayName("Given standard HTTP methods, when inferring capability, then returns correct capability type")
    void standardHttpMethodsMapToCapabilities() {
        assertThat(Capability.fromMethod("GET")).isEqualTo(Capability.READ);
        assertThat(Capability.fromMethod("HEAD")).isEqualTo(Capability.READ);
        assertThat(Capability.fromMethod("OPTIONS")).isEqualTo(Capability.READ);
        
        assertThat(Capability.fromMethod("POST")).isEqualTo(Capability.WRITE);
        assertThat(Capability.fromMethod("PUT")).isEqualTo(Capability.WRITE);
        assertThat(Capability.fromMethod("PATCH")).isEqualTo(Capability.WRITE);
        assertThat(Capability.fromMethod("DELETE")).isEqualTo(Capability.WRITE);
    }

    @Test
    @DisplayName("Given lowercase HTTP method, when inferring capability, then converts to uppercase and maps correctly")
    void lowercaseMethodsAreHandled() {
        assertThat(Capability.fromMethod("get")).isEqualTo(Capability.READ);
        assertThat(Capability.fromMethod("post")).isEqualTo(Capability.WRITE);
    }

    @Test
    @DisplayName("Given unknown HTTP method, when inferring capability, then throws IllegalArgumentException")
    void unknownMethodThrowsException() {
        assertThatThrownBy(() -> Capability.fromMethod("TRACE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot infer capability from method: TRACE");
        
        assertThatThrownBy(() -> Capability.fromMethod("CONNECT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot infer capability from method: CONNECT");
        
        assertThatThrownBy(() -> Capability.fromMethod("PROPFIND"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot infer capability from method: PROPFIND");
    }

    @Test
    @DisplayName("Given null method, when inferring capability, then throws NullPointerException")
    void nullMethodThrowsException() {
        assertThatThrownBy(() -> Capability.fromMethod(null))
            .isInstanceOf(NullPointerException.class);
    }
}
