package ee.openeid.siga.common.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WireMockTest(httpsEnabled = true)
class HttpClientImplTlsHandshakeFailureTest {

    private HttpClientImpl httpClient;

    private static final X509TrustManager UNTRUSTED_TRUST_MANAGER = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Server certificate not trusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        int port = wireMockRuntimeInfo.getHttpsPort();

        stubFor(get("/path")
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        httpClient = new HttpClientImpl(createUntrustedWebClient(port));
    }

    @Test
    void get_WhenServerCertificateUntrusted_HttpClientTlsHandshakeExceptionIsThrown() {
        HttpClientTlsHandshakeException ex = assertThrows(
                HttpClientTlsHandshakeException.class,
                () -> httpClient.get("/path", String.class)
        );

        assertEquals("TLS handshake failed", ex.getMessage());
        assertInstanceOf(SSLHandshakeException.class, ex.getCause());
    }

    @Test
    void post_WhenServerCertificateUntrusted_HttpClientTlsHandshakeExceptionIsThrown() {
        Object requestBody = new Object();

        HttpClientTlsHandshakeException ex = assertThrows(
                HttpClientTlsHandshakeException.class,
                () -> httpClient.post("/path", requestBody, byte[].class)
        );

        assertEquals("TLS handshake failed", ex.getMessage());
        assertInstanceOf(SSLHandshakeException.class, ex.getCause());
    }

    private static WebClient createUntrustedWebClient(int port) throws Exception {

        SimpleTrustManagerFactory tmf = new SimpleTrustManagerFactory() {
            @Override
            protected void engineInit(java.security.KeyStore keyStore) {
            }

            @Override
            protected void engineInit(javax.net.ssl.ManagerFactoryParameters managerFactoryParameters) {
            }

            @Override
            protected TrustManager[] engineGetTrustManagers() {
                return new TrustManager[]{UNTRUSTED_TRUST_MANAGER};
            }
        };

        SslContext sslContext = SslContextBuilder.forClient().trustManager(tmf).build();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .responseTimeout(Duration.ofSeconds(1))
                .secure(spec -> spec.sslContext(sslContext).handshakeTimeout(Duration.ofMillis(1000)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://localhost:" + port)
                .build();
    }
}
