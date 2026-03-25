package io.eventbob.example.microlith.dw.echo;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.eventbob.dropwizard.EventBobBundle;
import io.eventbob.dropwizard.adapter.RemoteCapability;
import io.eventbob.example.echo.EchoHandlerLifecycle;
import io.eventbob.example.lower.LowerHandlerLifecycle;

import java.net.URI;
import java.util.List;

/**
 * Echo Dropwizard microlith application.
 *
 * <p>This Dropwizard microlith provides echo, invert, and lower capabilities
 * locally via inline lifecycle wiring with manual (no-Spring) dependency injection,
 * and delegates upper to a remote microlith. It demonstrates the microlithic
 * microservice pattern: multiple capabilities co-located in a single deployable unit.
 * </p>
 */
public class EchoApplication extends Application<Configuration> {

    private final EventBobBundle bundle = new EventBobBundle(
        null,
        List.of(new EchoHandlerLifecycle(), new LowerHandlerLifecycle()),
        List.of(new RemoteCapability("upper", URI.create("http://localhost:8081")))
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
        new EchoApplication().run(args);
    }
}
