package io.eventbob.example.microlith.dw.upper;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.eventbob.dropwizard.EventBobBundle;
import io.eventbob.example.upper.UpperHandlerLifecycle;

import java.util.List;

/**
 * Upper Dropwizard microlith application.
 *
 * <p>This Dropwizard microlith provides the upper capability via inline
 * lifecycle wiring with manual (no-Spring) dependency injection. It demonstrates
 * the microlithic microservice pattern for a single-capability deployment.
 */
public class UpperApplication extends Application<Configuration> {

    private final EventBobBundle bundle = new EventBobBundle(
        null,
        List.of(new UpperHandlerLifecycle()),
        null
    );

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        bootstrap.addBundle(bundle);
    }

    @Override
    public void run(Configuration configuration, Environment environment) {
        // All wiring is handled by EventBobBundle.run()
    }

    public static void main(String[] args) throws Exception {
        new UpperApplication().run(args);
    }
}
