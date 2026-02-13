package io.eventbob.spring;

import io.eventbob.core.endpointresolution.Capability;

import java.util.Objects;

/**
 * Describes a capability discovered by scanning a JAR for EventHandler annotations.
 *
 * <p>This is the in-memory representation of capability metadata before
 * it's persisted to the registry database.
 */
public final class CapabilityDescriptor {
    private final String serviceName;
    private final Capability capability;
    private final int capabilityVersion;
    private final String method;
    private final String pathPattern;
    private final String handlerClassName;

    private CapabilityDescriptor(Builder builder) {
        this.serviceName = Objects.requireNonNull(builder.serviceName, "serviceName");
        this.capability = Objects.requireNonNull(builder.capability, "capability");
        this.capabilityVersion = builder.capabilityVersion;
        this.method = Objects.requireNonNull(builder.method, "method");
        this.pathPattern = Objects.requireNonNull(builder.pathPattern, "pathPattern");
        this.handlerClassName = Objects.requireNonNull(builder.handlerClassName, "handlerClassName");

        if (capabilityVersion <= 0) {
            throw new IllegalArgumentException("capabilityVersion must be positive: " + capabilityVersion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getServiceName() {
        return serviceName;
    }

    public Capability getCapability() {
        return capability;
    }

    public int getCapabilityVersion() {
        return capabilityVersion;
    }

    public String getMethod() {
        return method;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getHandlerClassName() {
        return handlerClassName;
    }

    /**
     * Routing key uniquely identifies a capability operation.
     */
    public String getRoutingKey() {
        return String.format("%s:%s:%s:%s",
            serviceName, capability, method, pathPattern);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapabilityDescriptor that = (CapabilityDescriptor) o;
        return capabilityVersion == that.capabilityVersion &&
            serviceName.equals(that.serviceName) &&
            capability == that.capability &&
            method.equals(that.method) &&
            pathPattern.equals(that.pathPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, capability, capabilityVersion, method, pathPattern);
    }

    @Override
    public String toString() {
        return String.format("%s %s %s v%d (%s)",
            serviceName, capability, method, capabilityVersion, pathPattern);
    }

    public static final class Builder {
        private String serviceName;
        private Capability capability;
        private int capabilityVersion;
        private String method;
        private String pathPattern;
        private String handlerClassName;

        private Builder() {}

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder capability(Capability capability) {
            this.capability = capability;
            return this;
        }

        public Builder capabilityVersion(int capabilityVersion) {
            this.capabilityVersion = capabilityVersion;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder pathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
            return this;
        }

        public Builder handlerClassName(String handlerClassName) {
            this.handlerClassName = handlerClassName;
            return this;
        }

        public CapabilityDescriptor build() {
            return new CapabilityDescriptor(this);
        }
    }
}
