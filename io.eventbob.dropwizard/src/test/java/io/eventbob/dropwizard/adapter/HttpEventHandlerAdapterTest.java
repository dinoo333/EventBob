package io.eventbob.dropwizard.adapter;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.eventbob.core.Event;
import io.eventbob.core.EventHandlingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEventHandlerAdapterTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private HttpEventHandlerAdapter adapter;
    private URI remoteEndpoint;

    @BeforeEach
    void setUp() {
        remoteEndpoint = URI.create("http://localhost:" + wireMock.getPort());
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        adapter = new HttpEventHandlerAdapter(remoteEndpoint, httpClient);
    }

    @Test
    void shouldSendEventAndReturnResponseOnSuccess() throws Exception {
        Event inputEvent = Event.builder()
            .source("test")
            .target("upper")
            .payload("hello")
            .build();

        String responseJson = """
            {
              "source": "test",
              "target": "upper",
              "parameters": {},
              "metadata": {},
              "payload": "HELLO"
            }
            """;

        wireMock.stubFor(post(urlEqualTo("/events"))
            .withHeader("Content-Type", equalTo("application/json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseJson)));

        Event result = adapter.handle(inputEvent, null);

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo("test");
        assertThat(result.getTarget()).isEqualTo("upper");
        assertThat(result.getPayload()).isEqualTo("HELLO");

        wireMock.verify(postRequestedFor(urlEqualTo("/events"))
            .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    void shouldThrowEventHandlingExceptionOn404() {
        Event inputEvent = Event.builder()
            .source("test")
            .target("unknown")
            .build();

        wireMock.stubFor(post(urlEqualTo("/events"))
            .willReturn(aResponse()
                .withStatus(404)
                .withBody("Not Found")));

        assertThatThrownBy(() -> adapter.handle(inputEvent, null))
            .isInstanceOf(EventHandlingException.class)
            .hasMessageContaining("404")
            .hasMessageContaining("Client error from remote endpoint");
    }

    @Test
    void shouldThrowEventHandlingExceptionOn500() {
        Event inputEvent = Event.builder()
            .source("test")
            .target("failing")
            .build();

        wireMock.stubFor(post(urlEqualTo("/events"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        assertThatThrownBy(() -> adapter.handle(inputEvent, null))
            .isInstanceOf(EventHandlingException.class)
            .hasMessageContaining("500")
            .hasMessageContaining("Server error from remote endpoint");
    }

    @Test
    void shouldThrowEventHandlingExceptionOnNetworkTimeout() {
        Event inputEvent = Event.builder()
            .source("test")
            .target("slow")
            .build();

        // Use an invalid host to trigger connection timeout
        URI invalidEndpoint = URI.create("http://192.0.2.1:9999"); // TEST-NET-1 (guaranteed non-routable)
        HttpClient timeoutClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(500))
            .build();
        HttpEventHandlerAdapter timeoutAdapter = new HttpEventHandlerAdapter(invalidEndpoint, timeoutClient);

        assertThatThrownBy(() -> timeoutAdapter.handle(inputEvent, null))
            .isInstanceOf(EventHandlingException.class)
            .hasMessageContaining("Network error calling remote endpoint")
            .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void shouldThrowEventHandlingExceptionOnMalformedResponse() {
        Event inputEvent = Event.builder()
            .source("test")
            .target("malformed")
            .build();

        wireMock.stubFor(post(urlEqualTo("/events"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{invalid json")));

        assertThatThrownBy(() -> adapter.handle(inputEvent, null))
            .isInstanceOf(EventHandlingException.class)
            .hasMessageContaining("Failed to parse response from remote endpoint");
    }

    @Test
    void shouldPreserveEventParameters() throws Exception {
        Event inputEvent = Event.builder()
            .source("test")
            .target("upper")
            .parameters(Map.of("timeout", "5000"))
            .payload("hello")
            .build();

        String responseJson = """
            {
              "source": "test",
              "target": "upper",
              "parameters": {"timeout": "5000"},
              "metadata": {},
              "payload": "HELLO"
            }
            """;

        wireMock.stubFor(post(urlEqualTo("/events"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseJson)));

        Event result = adapter.handle(inputEvent, null);

        assertThat(result.getParameters()).containsEntry("timeout", "5000");
    }

    @Test
    void shouldPreserveEventMetadata() throws Exception {
        Event inputEvent = Event.builder()
            .source("test")
            .target("upper")
            .metadata(Map.of("traceId", "abc123"))
            .payload("hello")
            .build();

        String responseJson = """
            {
              "source": "test",
              "target": "upper",
              "parameters": {},
              "metadata": {"traceId": "abc123"},
              "payload": "HELLO"
            }
            """;

        wireMock.stubFor(post(urlEqualTo("/events"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseJson)));

        Event result = adapter.handle(inputEvent, null);

        assertThat(result.getMetadata()).containsEntry("traceId", "abc123");
    }
}
