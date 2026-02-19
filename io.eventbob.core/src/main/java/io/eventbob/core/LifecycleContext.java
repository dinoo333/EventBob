package io.eventbob.core;

import java.util.Map;
import java.util.Optional;

/**
 * Context provided to handlers during initialization.
 * <p>
 * Provides handler-specific configuration, the dispatcher for sending events,
 * and optional framework-specific context (Spring ApplicationContext, Dropwizard
 * Environment, etc.) when available.
 * </p>
 * <p>
 * <b>Framework-Agnostic Design:</b>
 * </p>
 * <p>
 * The core EventBob framework does not depend on any specific framework (Spring,
 * Dropwizard, Guice, etc.). The {@link #getFrameworkContext(Class)} method provides
 * an extension point for microliths that use frameworks, allowing handlers to
 * optionally access framework-specific resources.
 * </p>
 * <p>
 * <b>Usage Examples:</b>
 * </p>
 * <pre>
 * // Spring handler using framework context
 * public void initialize(LifecycleContext context) {
 *     Optional&lt;ApplicationContext&gt; springContext =
 *         context.getFrameworkContext(ApplicationContext.class);
 *
 *     if (springContext.isPresent()) {
 *         // Use parent Spring context
 *         this.handler = springContext.get().getBean(MyHandler.class);
 *     } else {
 *         // Create own Spring context
 *         this.handler = createStandaloneSpringHandler(context);
 *     }
 * }
 *
 * // Manual handler ignoring framework context
 * public void initialize(LifecycleContext context) {
 *     String dbUrl = (String) context.getConfiguration().get("database.url");
 *     DataSource ds = createDataSource(dbUrl);
 *     this.handler = new MyHandler(ds, context.getDispatcher());
 * }
 * </pre>
 *
 * @see HandlerLifecycle
 */
public interface LifecycleContext {

    /**
     * Returns handler-specific configuration.
     * <p>
     * Configuration is loaded from the handler JAR's {@code application.yml} file
     * and provided as a Map. Handlers can parse this configuration into typed objects
     * using their chosen framework (Spring's @ConfigurationProperties, Dropwizard's
     * Configuration classes, Jackson ObjectMapper, etc.).
     * </p>
     * <p>
     * Example application.yml:
     * </p>
     * <pre>
     * database:
     *   url: jdbc:postgresql://localhost/mydb
     *   username: user
     *   password: ${DB_PASSWORD}
     * email:
     *   smtpHost: smtp.example.com
     * </pre>
     *
     * @return configuration map (never null, may be empty)
     */
    Map<String, Object> getConfiguration();

    /**
     * Returns the dispatcher for sending events to other capabilities.
     * <p>
     * Handlers use the dispatcher to invoke other capabilities during event processing.
     * The dispatcher handles routing (local or remote) transparently.
     * </p>
     *
     * @return the dispatcher for this microlith (never null)
     */
    Dispatcher getDispatcher();

    /**
     * Returns framework-specific context if available.
     * <p>
     * This method allows handlers to access framework-specific resources provided by
     * the microlith (Spring ApplicationContext, Dropwizard Environment, etc.). The
     * context type is generic to maintain framework-agnosticism in the core.
     * </p>
     * <p>
     * <b>Common framework contexts:</b>
     * </p>
     * <ul>
     *   <li>Spring Boot: {@code ApplicationContext}</li>
     *   <li>Dropwizard: {@code Environment}</li>
     *   <li>Guice: {@code Injector}</li>
     * </ul>
     * <p>
     * Handlers should not assume framework context is available. If the microlith
     * does not use a framework, or uses a different framework, this returns empty.
     * </p>
     * <p>
     * <b>Example usage:</b>
     * </p>
     * <pre>
     * // Spring handler
     * Optional&lt;ApplicationContext&gt; spring =
     *     context.getFrameworkContext(ApplicationContext.class);
     *
     * if (spring.isPresent()) {
     *     // Handler can use microlith's Spring context as parent
     *     AnnotationConfigApplicationContext childContext =
     *         new AnnotationConfigApplicationContext();
     *     childContext.setParent(spring.get());
     *     // ...
     * }
     * </pre>
     *
     * @param type the expected type of the framework context
     * @param <T> the framework context type
     * @return the framework context if available and matches the requested type, otherwise empty
     */
    <T> Optional<T> getFrameworkContext(Class<T> type);
}
