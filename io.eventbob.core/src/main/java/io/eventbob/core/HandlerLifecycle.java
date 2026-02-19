package io.eventbob.core;

/**
 * Lifecycle contract for handler initialization and cleanup.
 * <p>
 * EventBob is a container for embedded microservices. Like other containers
 * (Servlet, Spring, Dropwizard), it defines a lifecycle contract that handlers
 * must implement to integrate with the container.
 * </p>
 * <p>
 * Handler JARs provide an implementation of this abstract class that knows how
 * to initialize the handler using the chosen framework (Spring, Dropwizard, manual
 * wiring, etc.). EventBob calls the lifecycle methods at appropriate times:
 * </p>
 * <ol>
 *   <li>{@link #initialize(LifecycleContext)} - Prepare handler with configuration and dependencies</li>
 *   <li>{@link #getHandler()} - Retrieve the initialized handler for event processing</li>
 *   <li>{@link #shutdown()} - Clean up resources when EventBob shuts down</li>
 * </ol>
 * <p>
 * <b>Implementation Examples:</b>
 * </p>
 * <pre>
 * // Spring-based handler
 * public class EmailHandlerLifecycle extends HandlerLifecycle {
 *     private ApplicationContext springContext;
 *     private EmailHandler handler;
 *
 *     public void initialize(LifecycleContext context) throws Exception {
 *         AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
 *         ctx.register(EmailConfig.class);
 *         ctx.refresh();
 *         this.springContext = ctx;
 *         this.handler = ctx.getBean(EmailHandler.class);
 *     }
 *
 *     public EventHandler getHandler() {
 *         return handler;
 *     }
 *
 *     public void shutdown() throws Exception {
 *         if (springContext instanceof ConfigurableApplicationContext) {
 *             ((ConfigurableApplicationContext) springContext).close();
 *         }
 *     }
 * }
 *
 * // Manual wiring
 * public class SimpleHandlerLifecycle extends HandlerLifecycle {
 *     private SimpleHandler handler;
 *
 *     public void initialize(LifecycleContext context) {
 *         String config = (String) context.getConfiguration().get("someKey");
 *         this.handler = new SimpleHandler(config, context.getDispatcher());
 *     }
 *
 *     public EventHandler getHandler() {
 *         return handler;
 *     }
 *
 *     public void shutdown() {
 *         // No cleanup needed
 *     }
 * }
 * </pre>
 * <p>
 * <b>Why abstract class instead of interface?</b>
 * </p>
 * <p>
 * Abstract class allows evolution without breaking existing implementations. Future
 * versions of EventBob can add new lifecycle methods (health checks, metrics, config
 * reload) with default implementations, preserving backward compatibility.
 * </p>
 *
 * @see LifecycleContext
 * @see EventHandler
 */
public abstract class HandlerLifecycle {

    /**
     * Initialize the handler with the provided context.
     * <p>
     * This method is called once after the handler JAR is loaded. Implementations
     * should use the context to obtain configuration, dependencies, and framework-specific
     * resources needed to create and wire the handler.
     * </p>
     * <p>
     * Implementations may perform expensive operations here (database connections,
     * Spring context startup, dependency injection) as this is called only once
     * during handler registration, not per-event.
     * </p>
     *
     * @param context provides configuration, dispatcher, and optional framework context
     * @throws Exception if initialization fails (handler will not be registered)
     */
    public abstract void initialize(LifecycleContext context) throws Exception;

    /**
     * Returns the initialized handler.
     * <p>
     * Called after {@link #initialize(LifecycleContext)} completes successfully.
     * The returned handler will be used to process events for the declared capabilities.
     * </p>
     *
     * @return the initialized EventHandler instance
     */
    public abstract EventHandler getHandler();

    /**
     * Shuts down the handler and releases resources.
     * <p>
     * Called when EventBob is shutting down. Implementations should close database
     * connections, shutdown thread pools, close Spring contexts, and release any other
     * resources acquired during initialization.
     * </p>
     * <p>
     * This method should be idempotent (safe to call multiple times) and should not
     * throw exceptions that would prevent other handlers from shutting down cleanly.
     * </p>
     *
     * @throws Exception if shutdown fails (logged but does not prevent shutdown)
     */
    public abstract void shutdown() throws Exception;
}
