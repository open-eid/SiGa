package ee.openeid.siga.common.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpClientImplTimeoutTest {

    @Test
    void testConnectionTimeout() {
        Duration connectionTimeout = Duration.ofSeconds(2);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectionTimeout.toMillis()));

        WebClient webClient = WebClient.builder()
                // Non-routable IP Address
                .baseUrl("http://10.255.255.1:99")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        HttpClientImpl httpClientImpl = new HttpClientImpl(webClient);

        HttpClientConnectionException caughtException = assertThrows(
                HttpClientConnectionException.class, () -> httpClientImpl.get("/path", String.class)
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

        HttpClientTimeoutException caughtException = assertThrows(
                HttpClientTimeoutException.class, () -> httpClientImpl.get(endpoint, String.class)
        );

        assertThat(caughtException.getCause(), instanceOf(ReadTimeoutException.class));
    }

    @Test
    void post_whenReadTimeout_thenHttpClientTimeoutExceptionIsThrown() {
        int port = findFreePort();
        String host = "localhost";

        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(port));
        wireMockServer.start();
        WireMock.configureFor(host, wireMockServer.port());

        WireMockRuntimeInfo wireMockRuntimeInfo = new WireMockRuntimeInfo(wireMockServer);

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockRuntimeInfo.getHttpPort())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofMillis(500))
                ))
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        WireMock.stubFor(
                WireMock.post("/path")
                        .willReturn(
                                WireMock.aResponse()
                                        .withFixedDelay(5000)
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"validationConclusion\":{\"valid\":true}}")
                        )
        );

        String requestBody = "";

        HttpClientTimeoutException ex = assertThrows(
                HttpClientTimeoutException.class,
                () -> httpClient.post("/path", requestBody, byte[].class)
        );

        assertEquals("Read timeout occurred", ex.getMessage());
        Throwable rootCause = getRootCause(ex.getCause());
        assertInstanceOf(ReadTimeoutException.class, rootCause);
    }

    @Test
    void get_whenReadTimeout_thenHttpClientTimeoutExceptionIsThrown() {
        int port = findFreePort();
        String host = "localhost";

        WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(port));
        wireMockServer.start();
        WireMock.configureFor(host, wireMockServer.port());

        WireMockRuntimeInfo wireMockRuntimeInfo = new WireMockRuntimeInfo(wireMockServer);

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockRuntimeInfo.getHttpPort())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofMillis(500))
                ))
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        WireMock.stubFor(
                WireMock.get("/path")
                        .willReturn(
                                WireMock.aResponse()
                                        .withFixedDelay(5000)
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"validationConclusion\":{\"valid\":true}}")
                        )
        );

        HttpClientTimeoutException ex = assertThrows(
                HttpClientTimeoutException.class,
                () -> httpClient.get("/validate", byte[].class)
        );

        assertInstanceOf(ReadTimeoutException.class, getRootCause(ex));
        assertEquals("Read timeout occurred", ex.getMessage());
    }


    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find a free port", e);
        }
    }

    private static Throwable getRootCause(Throwable throwable) {
        while (throwable.getCause() != null && throwable.getCause() != throwable) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

}
