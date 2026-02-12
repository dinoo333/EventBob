package io.eventbob.core;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture tests enforcing package boundaries and dependency rules for io.eventbob.core.
 *
 * <p><b>IMPORTANT:</b> This test only analyzes the core module. Each module should have its own
 * ArchUnit test to prevent hidden drift. When adding new modules (e.g., io.eventbob.registry),
 * create a corresponding ArchitectureTest (e.g., RegistryArchitectureTest) following this pattern.
 *
 * <p>This module contains TWO SUBDOMAINS within the Event Routing bounded context:
 * <ul>
 *   <li><b>eventrouting</b> — Routes events by target to handlers</li>
 *   <li><b>endpointresolution</b> — Resolves service operations to physical endpoints</li>
 * </ul>
 *
 * <p>These tests prevent architectural drift by failing the build if:
 * <ul>
 *   <li>Endpoint Resolution depends on Event Routing (wrong direction)</li>
 *   <li>Cyclic dependencies exist between packages</li>
 *   <li>Core depends on external libraries (except JDK and SLF4J)</li>
 *   <li>Core depends on implementation modules (Spring, Dropwizard)</li>
 * </ul>
 *
 * <p><b>Dependency Rule:</b> eventrouting → endpointresolution (one-way, stable dependency)
 *
 * <p><b>Martin Metrics:</b> Generated dependency graph shows package health (Abstractedness, Instability, Distance)
 * within THIS MODULE ONLY. Does not include other modules.
 */
@DisplayName("Core Module Architecture Rules")
class CoreArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.eventbob.core");
    }

    @Test
    @DisplayName("Event Routing subdomain can depend on Endpoint Resolution subdomain")
    void eventRoutingCanDependOnEndpointResolution() {
        // EventHandlerCapability annotation references Capability enum from endpointresolution
        // This is acceptable - stable dependency direction (unstable → stable)
        ArchRule rule = classes()
            .that().resideInAPackage("..eventrouting..")
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "io.eventbob.core.eventrouting..",
                    "io.eventbob.core.endpointresolution",
                    "java..",
                    "org.slf4j.."
                );

        rule.check(classes);
    }

    @Test
    @DisplayName("Endpoint Resolution subdomain should not depend on Event Routing subdomain")
    void endpointResolutionShouldNotDependOnEventRouting() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..endpointresolution..")
            .should().dependOnClassesThat().resideInAPackage("..eventrouting..")
            .because("Endpoint Resolution is a stable port, must not depend on unstable routing implementation");

        rule.check(classes);
    }

    @Test
    @DisplayName("Core module should not depend on implementation modules")
    void coreShouldNotDependOnImplementations() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.eventbob.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..spring..",
                "..dropwizard.."
            )
            .because("Core defines ports, implementation modules inject adapters");

        rule.check(classes);
    }

    @Test
    @DisplayName("Core packages should be free of cyclic dependencies")
    void noCyclicDependencies() {
        ArchRule rule = slices()
            .matching("io.eventbob.core.(*)..")
            .should().beFreeOfCycles();

        rule.check(classes);
    }

    @Test
    @DisplayName("Core should only depend on JDK and SLF4J (zero external dependencies)")
    void coreShouldBeIndependent() {
        ArchRule rule = classes()
            .that().resideInAPackage("io.eventbob.core..")
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "io.eventbob.core..",
                    "java..",
                    "org.slf4j.."  // Logging abstraction only
                )
            .as("Core should have zero external dependencies (except JDK and SLF4J)");

        rule.check(classes);
    }

    @Test
    @DisplayName("Package naming should follow subdomain convention")
    void packagesShouldFollowNamingConvention() {
        ArchRule rule = classes()
            .that().resideInAPackage("io.eventbob.core..")
            .should().resideInAnyPackage(
                "io.eventbob.core.eventrouting..",
                "io.eventbob.core.endpointresolution.."
            )
            .as("All core classes should be in a recognized subdomain package");

        rule.check(classes);
    }

    // ========== Dependency Diagram Generation ==========

    /**
     * Martin Metric for a package.
     *
     * <p>Abstractedness (A): ratio of abstract classes to total classes
     * <p>Instability (I): ratio of outgoing dependencies to total dependencies
     * <p>Distance (D): |A + I - 1| - how far from the ideal line
     *
     * <p>Ideal packages are either:
     * <ul>
     *   <li>Abstract and stable (A=1, I=0) - pure abstractions</li>
     *   <li>Concrete and unstable (A=0, I=1) - pure implementations</li>
     * </ul>
     */
    record MartinMetric(String name, double abstractedness, double instability) {
        @Override
        public String toString() {
            return String.format("%s[\"%s<br/>D=%.2f (A=%.2f, I=%.2f)\"]",
                name, name, Math.abs(abstractedness + instability - 1), abstractedness, instability);
        }
    }

    record Dependency(MartinMetric from, MartinMetric to) { }

    static Comparator<MartinMetric> compareMetric = Comparator.comparingDouble(MartinMetric::instability).reversed()
        .thenComparing(MartinMetric::name);

    static Comparator<Dependency> compareFromDependency = (o1, o2) -> compareMetric.compare(o1.from(), o2.from());
    static Comparator<Dependency> compareToDependency = (o1, o2) -> compareMetric.compare(o1.to(), o2.to());

    @Test
    @DisplayName("Generate Mermaid dependency graph with Martin metrics")
    void generateMermaidDependencyGraph() throws IOException {
        var coreClasses = new ClassFileImporter()
            .withImportOption(DO_NOT_INCLUDE_TESTS)
            .importPackages("io.eventbob.core");

        var dependencies = new ArrayList<Dependency>();

        coreClasses.stream()
            .map(JavaClass::getPackage)
            .distinct()
            .forEach(pkg -> {
                if (pkg.getName().startsWith("io.eventbob.core")) {
                    pkg.getPackageDependenciesFromThisPackage().forEach(dependency -> {
                        if (dependency.getName().startsWith("io.eventbob.core")) {
                            dependencies.add(new Dependency(martinMetric(pkg), martinMetric(dependency)));
                        }
                    });
                }
            });

        // Sort by instability (most unstable first) for better diagram readability
        dependencies.sort(compareFromDependency.thenComparing(compareToDependency));

        String diagram = """
            # EventBob Core Dependency Graph

            Package dependencies with Martin Metrics:
            - **A** (Abstractedness): ratio of abstract classes to total classes
            - **I** (Instability): ratio of outgoing to total dependencies
            - **D** (Distance): |A + I - 1| (distance from ideal line)

            Ideal packages are either:
            - Abstract and stable (A=1, I=0) - pure abstractions/ports
            - Concrete and unstable (A=0, I=1) - pure implementations

            *Generated by CoreArchitectureTest.generateMermaidDependencyGraph().*
            *Do not modify this file directly.*

            ```mermaid
            graph TD
            """
            + String.join("", dependencies.stream()
                .map(d -> String.format("  %s --> %s\n", d.from(), d.to()))
                .toList())
            + "```\n\n";

        Files.writeString(
            Paths.get("docs/core_dependency_graph.md"),
            diagram,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        System.out.println("Generated dependency graph at: docs/core_dependency_graph.md");
    }

    private MartinMetric martinMetric(JavaPackage javaPackage) {
        String name = javaPackage.getName().substring("io.eventbob.core.".length());

        // Efferent coupling (Ce): outgoing dependencies
        double ce = javaPackage.getPackageDependenciesFromThisPackage().stream()
            .filter(dep -> dep.getName().startsWith("io.eventbob.core"))
            .count();

        // Afferent coupling (Ca): incoming dependencies
        double ca = javaPackage.getClassDependenciesToThisPackage().stream()
            .filter(dep -> dep.getOriginClass().getPackageName().startsWith("io.eventbob.core"))
            .count();

        // Instability: I = Ce / (Ca + Ce)
        double instability = (ca + ce) == 0 ? 0 : ce / (ca + ce);

        // Total classes in package
        double totalClasses = javaPackage.getClasses().size();

        // Abstract classes (interfaces + abstract classes)
        double abstractClasses = javaPackage.getClasses().stream()
            .filter(clazz -> clazz.isInterface() || clazz.getModifiers().contains(JavaModifier.ABSTRACT))
            .count();

        // Abstractedness: A = abstract classes / total classes
        double abstractedness = totalClasses == 0 ? 0 : abstractClasses / totalClasses;

        return new MartinMetric(name, abstractedness, instability);
    }
}
