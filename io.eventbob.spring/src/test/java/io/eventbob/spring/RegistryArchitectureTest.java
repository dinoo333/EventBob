package io.eventbob.spring;

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

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture tests enforcing dependency rules for io.eventbob.spring module.
 *
 * <p><b>IMPORTANT:</b> This test only analyzes the registry module. Each module should have its own
 * ArchUnit test to prevent hidden drift.
 *
 * <p>This module implements the Service Registry bounded context, which:
 * <ul>
 *   <li>Scans JARs for capability declarations (via ClassGraph)</li>
 *   <li>Persists capability metadata to PostgreSQL (via Spring JDBC)</li>
 *   <li>Provides routing lookup for capability-based endpoint resolution</li>
 * </ul>
 *
 * <p>These tests prevent architectural drift by failing the build if:
 * <ul>
 *   <li>Registry depends on other implementation modules (future: api, gateway, etc.)</li>
 *   <li>Cyclic dependencies exist between registry packages</li>
 *   <li>Registry violates dependency boundaries</li>
 * </ul>
 *
 * <p><b>Dependency Rule:</b> Registry → Core (one-way, depends on domain abstractions)
 *
 * <p><b>Allowed External Dependencies:</b>
 * <ul>
 *   <li>io.eventbob.core.* — Domain model and port abstractions</li>
 *   <li>Spring Boot — JDBC, transactions, dependency injection</li>
 *   <li>PostgreSQL driver — Database connectivity</li>
 *   <li>Flyway — Database migrations</li>
 *   <li>ClassGraph — JAR scanning for capability discovery</li>
 *   <li>SLF4J — Logging abstraction</li>
 *   <li>JDK — Standard library</li>
 * </ul>
 *
 * <p><b>Martin Metrics:</b> Generated dependency graph shows package health within THIS MODULE ONLY.
 */
@DisplayName("Registry Module Architecture Rules")
class RegistryArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.eventbob.spring");
    }

    @Test
    @DisplayName("Registry can depend on Core domain model")
    void registryCanDependOnCore() {
        ArchRule rule = classes()
            .that().resideInAPackage("io.eventbob.spring..")
            .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "io.eventbob.spring..",
                    "io.eventbob.core..",       // Domain model
                    "org.springframework..",     // Spring Boot infrastructure
                    "org.postgresql..",          // PostgreSQL driver
                    "org.flywaydb..",           // Database migrations
                    "io.github.classgraph..",   // JAR scanning
                    "org.slf4j..",              // Logging
                    "java..",                    // JDK
                    "javax.."                    // JDK extensions
                )
            .as("Registry should only depend on Core, Spring, PostgreSQL, Flyway, ClassGraph, SLF4J, and JDK");

        rule.check(classes);
    }

    @Test
    @DisplayName("Registry should not depend on other implementation modules")
    void registryShouldNotDependOnOtherModules() {
        // This rule prevents coupling to future implementation modules
        // (e.g., io.eventbob.api, io.eventbob.gateway)
        ArchRule rule = noClasses()
            .that().resideInAPackage("io.eventbob.spring..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.eventbob.api..",
                "io.eventbob.gateway..",
                "io.eventbob.service.."
            )
            .because("Registry should not couple to other implementation modules");

        rule.check(classes);
    }

    @Test
    @DisplayName("Registry packages should be free of cyclic dependencies")
    void noCyclicDependencies() {
        // Registry currently has a flat package structure (no subpackages)
        // This test will become relevant when subpackages are introduced
        ArchRule rule = slices()
            .matching("io.eventbob.spring.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

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
        var registryClasses = new ClassFileImporter()
            .withImportOption(DO_NOT_INCLUDE_TESTS)
            .importPackages("io.eventbob.spring");

        var dependencies = new ArrayList<Dependency>();

        registryClasses.stream()
            .map(JavaClass::getPackage)
            .distinct()
            .forEach(pkg -> {
                if (pkg.getName().startsWith("io.eventbob.spring")) {
                    pkg.getPackageDependenciesFromThisPackage().forEach(dependency -> {
                        if (dependency.getName().startsWith("io.eventbob.spring")) {
                            dependencies.add(new Dependency(martinMetric(pkg), martinMetric(dependency)));
                        }
                    });
                }
            });

        // Sort by instability (most unstable first) for better diagram readability
        dependencies.sort(compareFromDependency.thenComparing(compareToDependency));

        String dependencyGraph = dependencies.isEmpty()
            ? "  registry[\"registry<br/>D=0.00 (A=0.00, I=0.00)\"]\n"
            : String.join("", dependencies.stream()
                .map(d -> String.format("  %s --> %s\n", d.from(), d.to()))
                .toList());

        String diagram = """
            # EventBob Registry Dependency Graph

            Package dependencies with Martin Metrics:
            - **A** (Abstractedness): ratio of abstract classes to total classes
            - **I** (Instability): ratio of outgoing to total dependencies
            - **D** (Distance): |A + I - 1| (distance from ideal line)

            Ideal packages are either:
            - Abstract and stable (A=1, I=0) - pure abstractions/ports
            - Concrete and unstable (A=0, I=1) - pure implementations

            *Generated by RegistryArchitectureTest.generateMermaidDependencyGraph().*
            *Do not modify this file directly.*

            ```mermaid
            graph TD
            """
            + dependencyGraph
            + "```\n\n";

        var outputPath = Paths.get("docs/registry_dependency_graph.md");
        Files.createDirectories(outputPath.getParent());
        Files.writeString(
            outputPath,
            diagram,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        System.out.println("Generated dependency graph at: docs/registry_dependency_graph.md");
    }

    private MartinMetric martinMetric(JavaPackage javaPackage) {
        String name = javaPackage.getName().substring("io.eventbob.spring.".length());
        if (name.isEmpty()) {
            name = "registry";
        }

        // Efferent coupling (Ce): outgoing dependencies
        double ce = javaPackage.getPackageDependenciesFromThisPackage().stream()
            .filter(dep -> dep.getName().startsWith("io.eventbob.spring"))
            .count();

        // Afferent coupling (Ca): incoming dependencies
        double ca = javaPackage.getClassDependenciesToThisPackage().stream()
            .filter(dep -> dep.getOriginClass().getPackageName().startsWith("io.eventbob.spring"))
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
