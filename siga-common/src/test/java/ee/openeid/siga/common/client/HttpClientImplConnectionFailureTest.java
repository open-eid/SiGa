package ee.openeid.siga.common.client;

import io.netty.resolver.dns.DnsErrorCauseException;
import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientImplConnectionFailureTest {

    @Test
    void post_WhenDnsErrorCauseOrDnsNameResolverTimeoutOrUnknownHost_HttpClientConnectionExceptionIsThrown() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://nonexistent.test")
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);
        Object requestBody = new Object();

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.post("/path", requestBody, byte[].class));

        // Depending on the environment (local vs Jenkins), DNS resolution may fail differently:
        // - Locally, it might throw UnknownHostException
        // - On Jenkins, it might throw DnsNameResolverTimeoutException || DnsErrorCause
        // The test ensures that all cases are correctly wrapped in a HttpClientConnectionException
        assertTrue(ex.getCause() instanceof DnsErrorCauseException ||
                ex.getCause() instanceof DnsNameResolverTimeoutException ||
                ex.getCause() instanceof UnknownHostException);
        assertEquals("Service unreachable", ex.getMessage());
    }

    @Test
    void get_WhenDnsErrorCauseOrDnsNameResolverTimeoutOrUnknownHost_HttpClientConnectionExceptionIsThrown() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://nonexistent.test")
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.get("/path", byte[].class));

        // Depending on the environment (local vs Jenkins), DNS resolution may fail differently:
        // - Locally, it might throw UnknownHostException
        // - On Jenkins, it might throw DnsNameResolverTimeoutException || DnsErrorCause
        // The test ensures that all cases are correctly wrapped in a HttpClientConnectionException
        assertTrue(ex.getCause() instanceof DnsErrorCauseException ||
                ex.getCause() instanceof DnsNameResolverTimeoutException ||
                ex.getCause() instanceof UnknownHostException);
        assertEquals("Service unreachable", ex.getMessage());
    }

    @Test
    void get_WhenTargetPortIsNotOpen_HttpClientConnectionExceptionIsThrown() {
        int port = findFreePort();
        String url = "http://localhost:" + port;

        WebClient webClient = WebClient.builder()
                .baseUrl(url)
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.get("/path", byte[].class));


        assertEquals("Unable to connect to service", ex.getMessage());
        assertThat(ex.getCause()).isInstanceOf(ConnectException.class);
    }

    @Test
    void post_WhenTargetPortIsNotOpen_HttpClientConnectionExceptionIsThrown() {
        int port = findFreePort();
        String url = "http://localhost:" + port;
        Object requestBody = new Object();

        WebClient webClient = WebClient.builder()
                .baseUrl(url)
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.post("/path", requestBody, byte[].class));

        assertEquals("Unable to connect to service", ex.getMessage());
        assertThat(ex.getCause()).isInstanceOf(ConnectException.class);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find a free port", e);
        }
    }
}
