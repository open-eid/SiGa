package ee.openeid.siga.common.client;

import io.netty.resolver.dns.DnsErrorCauseException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientImplConnectionFailureTest {

    @Test
    void post_WhenDnsErrorCause_HttpClientConnectionExceptionIsThrown() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://hdsjhdsjdh.com")
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);
        Object requestBody = new Object();

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.post("http://hdsjhdsjdh.com", requestBody, byte[].class));

        assertEquals("Service unreachable", ex.getMessage());
        assertInstanceOf(DnsErrorCauseException.class, ex.getCause());

    }

    @Test
    void get_WhenDnsErrorCause_HttpClientConnectionExceptionIsThrown() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://hdsjhdsjdh.com")
                .build();

        HttpClientImpl httpClient = new HttpClientImpl(webClient);

        HttpClientConnectionException ex = assertThrows(HttpClientConnectionException.class,
                () -> httpClient.get("http://hdsjhdsjdh.com", byte[].class));

        assertEquals("Service unreachable", ex.getMessage());
        assertInstanceOf(DnsErrorCauseException.class, ex.getCause());

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
                () -> httpClient.get(url, byte[].class));


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
                () -> httpClient.post(url, requestBody, byte[].class));

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
