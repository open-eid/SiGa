package ee.openeid.siga.common.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpClientImplTimeoutTest {

    @Test
    void testConnectionTimeout() {
        int port = simulateServer();
        Duration connectionTimeout = Duration.ofSeconds(2);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectionTimeout.toMillis()));

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        HttpClientImpl httpClientImpl = new HttpClientImpl(webClient);

        WebClientRequestException caughtException = assertThrows(
                WebClientRequestException.class, () -> httpClientImpl.get("/path", String.class)
        );

        assertThat(caughtException.getCause(), instanceOf(ConnectTimeoutException.class));
        assertThat(caughtException.getCause().getMessage(), startsWith("connection timed out"));
    }

    @Test
    void testReadTimeout() {
        String host = "localhost";
        String endpoint = "/path";
        int port = findFreePort();
        Duration timeout = Duration.ofSeconds(3);

        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(port));
        wireMockServer.start();
        WireMock.configureFor(host, wireMockServer.port());

        stubFor(get(urlEqualTo(endpoint))
                .willReturn(aResponse().withFixedDelay((int) timeout.toMillis())));

        HttpClient httpClient = HttpClient.create()
                .doOnConnected(conn -> {
                    conn.addHandlerLast(new ReadTimeoutHandler(timeout.getSeconds() / 2, TimeUnit.SECONDS));
                    conn.addHandlerLast(new WriteTimeoutHandler(timeout.getSeconds() / 2, TimeUnit.SECONDS));
                });

        WebClient webClient = WebClient.builder()
                .baseUrl(format("http://%s:%d", host, port))
                .clientConnector(new ReactorClientHttpConnector(httpClient)).build();

        HttpClientImpl httpClientImpl = new HttpClientImpl(webClient);

        WebClientRequestException caughtException = assertThrows(
                WebClientRequestException.class, () -> httpClientImpl.get(endpoint, String.class)
        );

        assertThat(caughtException.getCause(), instanceOf(ReadTimeoutException.class));
    }

    private int simulateServer() {
        int port = findFreePort();
        try (ClientAndServer mockServer = ClientAndServer.startClientAndServer(port)) {
            mockServer.when(HttpRequest.request("/path"), Times.once())
                    .respond(HttpResponse.response().withDelay(Delay.milliseconds(5000)));
        }
        return port;
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find a free port", e);
        }
    }

}
