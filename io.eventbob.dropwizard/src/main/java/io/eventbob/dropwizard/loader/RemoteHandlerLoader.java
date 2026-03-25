package io.eventbob.dropwizard.loader;

import io.eventbob.core.EventHandler;
import io.eventbob.core.HandlerLoader;
import io.eventbob.dropwizard.adapter.HttpEventHandlerAdapter;
import io.eventbob.dropwizard.adapter.RemoteCapability;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads EventHandler implementations from remote capability endpoints.
 * <p>
 * This loader creates HTTP adapter instances that wrap remote service calls.
 * Each RemoteCapability is converted into an HttpEventHandlerAdapter, providing
 * location transparency: EventBob treats remote handlers identically to local ones.
 * </p>
 * <p>
 * Future extensions could support other transport mechanisms (gRPC, JMS, Kafka, etc.)
 * by inspecting the URI scheme and creating appropriate adapter types.
 * </p>
 */
public class RemoteHandlerLoader implements HandlerLoader {
    private final List<RemoteCapability> remoteCapabilities;
    private final HttpClient httpClient;

    /**
     * Creates a remote handler loader.
     *
     * @param remoteCapabilities list of remote capability endpoints to wrap as handlers
     * @param httpClient the HTTP client to use for remote calls
     */
    public RemoteHandlerLoader(List<RemoteCapability> remoteCapabilities, HttpClient httpClient) {
        this.remoteCapabilities = remoteCapabilities;
        this.httpClient = httpClient;
    }

    @Override
    public Map<String, EventHandler> loadHandlers() throws IOException {
        Map<String, EventHandler> handlers = new HashMap<>();

        for (RemoteCapability capability : remoteCapabilities) {
            EventHandler adapter = new HttpEventHandlerAdapter(capability.uri(), httpClient);
            handlers.put(capability.name(), adapter);
        }

        return handlers;
    }

    @Override
    public void close() throws Exception {
        // No resources to clean up - HttpClient is owned by caller
    }
}
