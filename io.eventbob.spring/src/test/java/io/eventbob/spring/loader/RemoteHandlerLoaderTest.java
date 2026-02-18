package io.eventbob.spring.loader;

import io.eventbob.core.EventHandler;
import io.eventbob.spring.adapter.RemoteCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteHandlerLoaderTest {

  private HttpClient httpClient;

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  @Test
  void shouldCreateAdapterForEachRemoteCapability() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("upper", URI.create("http://localhost:8080")),
        new RemoteCapability("lower", URI.create("http://localhost:8081"))
    );

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers).hasSize(2);
    assertThat(handlers).containsKeys("upper", "lower");
  }

  @Test
  void shouldUseCorrectCapabilityNames() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("upper", URI.create("http://localhost:8080")),
        new RemoteCapability("other", URI.create("http://localhost:9000"))
    );

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers).containsKeys("upper", "other");
    assertThat(handlers.get("upper")).isNotNull();
    assertThat(handlers.get("other")).isNotNull();
  }

  @Test
  void shouldReturnEmptyMapWhenNoRemoteCapabilities() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of();

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers).isEmpty();
  }

  @Test
  void shouldCreateHttpEventHandlerAdapterInstances() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("echo", URI.create("http://example.com:3000"))
    );

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers.get("echo")).isNotNull();
    assertThat(handlers.get("echo")).isInstanceOf(EventHandler.class);
  }

  @Test
  void shouldHandleMultipleCapabilitiesWithDifferentPorts() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("service-a", URI.create("http://localhost:8080")),
        new RemoteCapability("service-b", URI.create("http://localhost:8081")),
        new RemoteCapability("service-c", URI.create("http://localhost:8082"))
    );

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers).hasSize(3);
    assertThat(handlers).containsKeys("service-a", "service-b", "service-c");
  }

  @Test
  void shouldHandleRemoteCapabilitiesWithDifferentHosts() throws IOException {
    List<RemoteCapability> remoteCapabilities = List.of(
        new RemoteCapability("local", URI.create("http://localhost:8080")),
        new RemoteCapability("remote", URI.create("https://api.example.com"))
    );

    RemoteHandlerLoader loader = new RemoteHandlerLoader(remoteCapabilities, httpClient);
    Map<String, EventHandler> handlers = loader.loadHandlers();

    assertThat(handlers).hasSize(2);
    assertThat(handlers).containsKeys("local", "remote");
  }
}
