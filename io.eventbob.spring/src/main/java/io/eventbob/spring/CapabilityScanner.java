package io.eventbob.spring;

import io.eventbob.core.eventrouting.EventHandler;
import io.eventbob.core.eventrouting.EventHandlerCapability;
import io.eventbob.core.endpointresolution.Capability;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationParameterValueList;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans JARs for EventHandler classes annotated with @EventHandlerCapability.
 *
 * <p>This is used during macro-service startup to discover what capabilities
 * each loaded JAR provides.
 */
public class CapabilityScanner {
    private static final Logger log = LoggerFactory.getLogger(CapabilityScanner.class);

    /**
     * Scan a JAR file and extract all declared capabilities.
     *
     * @param jarPath path to the JAR file
     * @param parentClassLoader parent classloader (should have EventBob core classes)
     * @return list of capability descriptors found in the JAR
     * @throws Exception if JAR cannot be scanned
     */
    public List<CapabilityDescriptor> scanJar(Path jarPath, ClassLoader parentClassLoader)
        throws Exception {

        log.info("Scanning JAR for capabilities: {}", jarPath);

        // Create isolated classloader for this JAR
        URLClassLoader jarClassLoader = new URLClassLoader(
            new URL[]{jarPath.toUri().toURL()},
            parentClassLoader
        );

        List<CapabilityDescriptor> capabilities = new ArrayList<>();

        try (ScanResult scanResult = new ClassGraph()
            .overrideClassLoaders(jarClassLoader)
            .enableAnnotationInfo()
            .enableClassInfo()
            .scan()) {

            // Find all classes that implement EventHandler and have @EventHandlerCapability
            ClassInfoList handlerClasses = scanResult
                .getClassesImplementing(EventHandler.class.getName())
                .filter(ci -> ci.hasAnnotation(EventHandlerCapability.class.getName()));

            log.debug("Found {} annotated EventHandler classes in {}", handlerClasses.size(), jarPath.getFileName());

            for (ClassInfo classInfo : handlerClasses) {
                try {
                    List<CapabilityDescriptor> descriptors = parseCapabilityAnnotations(classInfo);
                    capabilities.addAll(descriptors);
                    for (CapabilityDescriptor descriptor : descriptors) {
                        log.debug("  - {}", descriptor);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse capability from class: {}", classInfo.getName(), e);
                    // Continue scanning despite individual failures
                }
            }
        }

        log.info("Scanned JAR {}: found {} capabilities", jarPath.getFileName(), capabilities.size());
        return capabilities;
    }

    /**
     * Parse @EventHandlerCapability annotation into list of CapabilityDescriptors.
     *
     * <p>A single handler class can declare multiple operations (e.g., "GET /item", "GET /item/{id}"),
     * so this method returns one descriptor per operation.
     *
     * @param classInfo handler class with @EventHandlerCapability annotation
     * @return list of capability descriptors (one per operation)
     */
    private List<CapabilityDescriptor> parseCapabilityAnnotations(ClassInfo classInfo) {
        AnnotationInfo annotation = classInfo.getAnnotationInfo(EventHandlerCapability.class.getName());
        AnnotationParameterValueList params = annotation.getParameterValues();

        String serviceName = (String) params.getValue("service");
        String capabilityStr = (String) params.getValue("capability");
        Object capabilityVersionObj = params.getValue("capabilityVersion");
        int capabilityVersion = (capabilityVersionObj != null) ? (Integer) capabilityVersionObj : 1;
        String[] operations = (String[]) params.getValue("operations");

        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("@EventHandlerCapability.service is required on " + classInfo.getName());
        }

        if (capabilityStr == null || capabilityStr.isBlank()) {
            throw new IllegalArgumentException("@EventHandlerCapability.capability is required on " + classInfo.getName());
        }

        if (operations == null || operations.length == 0) {
            throw new IllegalArgumentException("@EventHandlerCapability.operations is required on " + classInfo.getName());
        }

        Capability capability;
        try {
            capability = Capability.valueOf(capabilityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid capability '" + capabilityStr + "' on " + classInfo.getName() +
                    ". Valid values: READ, WRITE, ADMIN");
        }

        // Parse each operation string (format: "METHOD /path")
        // One descriptor per operation
        List<CapabilityDescriptor> descriptors = new ArrayList<>();
        for (String operation : operations) {
            String[] parts = operation.trim().split("\\s+", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Invalid operation format '" + operation + "' on " + classInfo.getName() +
                        ". Expected: 'METHOD /path'");
            }

            String method = parts[0].toUpperCase();
            String pathPattern = parts[1];

            descriptors.add(CapabilityDescriptor.builder()
                .serviceName(serviceName)
                .capability(capability)
                .capabilityVersion(capabilityVersion)
                .method(method)
                .pathPattern(pathPattern)
                .handlerClassName(classInfo.getName())
                .build());
        }

        log.debug("Handler {} declares {} operation(s)", classInfo.getName(), descriptors.size());
        return descriptors;
    }
}
