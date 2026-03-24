package io.eventbob.example.upper;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLifecycle;
import io.eventbob.core.LifecycleContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lifecycle implementation for UpperHandler with Spring dependency injection.
 * <p>
 * Demonstrates Spring integration pattern. The lifecycle creates an isolated
 * ApplicationContext and retrieves the fully-wired handler from Spring.
 * </p>
 * <p>
 * Each handler gets its own isolated ApplicationContext. This ensures no bean
 * pollution between handlers and allows each handler to define its own
 * configuration without affecting others.
 * </p>
 * <p>
 * The handler and service remain POJOs with no Spring annotations. All Spring
 * wiring happens here in the lifecycle via @Configuration and @Bean methods.
 * </p>
 */
public class UpperHandlerLifecycle extends HandlerLifecycle {
    private volatile UpperHandler handler;
    private volatile AnnotationConfigApplicationContext applicationContext;

    @Override
    public synchronized void initialize(LifecycleContext context) {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
            handler = null;
        }
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        boolean success = false;
        try {
            ctx.register(UpperHandlerConfiguration.class);
            ctx.refresh();
            handler = ctx.getBean(UpperHandler.class);
            applicationContext = ctx;
            success = true;
        } finally {
            if (!success) {
                ctx.close();
            }
        }
    }

    @Override
    public EventHandler getHandler() {
        return handler;
    }

    @Override
    public synchronized void shutdown() {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
            handler = null;
        }
    }

    /**
     * Spring configuration for Upper handler dependencies.
     * <p>
     * Defines beans for UpperService and UpperHandler.
     * </p>
     */
    @Configuration
    static class UpperHandlerConfiguration {
        @Bean
        public UpperService upperService() {
            return new UpperService();
        }

        @Bean
        public UpperHandler upperHandler(UpperService upperService) {
            return new UpperHandler(upperService);
        }
    }
}
