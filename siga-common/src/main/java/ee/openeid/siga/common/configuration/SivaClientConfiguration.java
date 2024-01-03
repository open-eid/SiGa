package ee.openeid.siga.common.configuration;

import ee.openeid.siga.common.client.HttpClientImpl;
import ee.openeid.siga.common.exception.TechnicalException;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties({SivaClientConfigurationProperties.class})
public class SivaClientConfiguration {

    @Bean
    public HttpClientImpl sivaHttpClient(SivaClientConfigurationProperties configuration) {
        WebClient webClient = WebClient
                .builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(
                        Math.toIntExact(configuration.getMaxInMemorySize().toBytes())
                ))
                .baseUrl(configuration.getUrl())
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(configuration)))
                .build();

        return new HttpClientImpl(webClient);
    }

    private static HttpClient createHttpClient(SivaClientConfigurationProperties configuration) {
        HttpClient httpClient = HttpClient.create()
                        .secure(t -> t.sslContext(createSslContext(configuration)));

        setHttpClientTimeouts(configuration, httpClient);

        return httpClient;
    }

    private static void setHttpClientTimeouts(SivaClientConfigurationProperties configuration, HttpClient httpClient) {

        if (configuration.getConnectionTimeout() != null) {
            final int connectionTimeoutMillis = (int) configuration.getConnectionTimeout().toMillis();
            httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis);
        }

        if (configuration.getWriteTimeout() != null) {
            final long writeTimeoutMillis = configuration.getWriteTimeout().toMillis();
            httpClient.doOnConnected(
                    connection -> connection.addHandlerFirst(new WriteTimeoutHandler(writeTimeoutMillis, TimeUnit.MILLISECONDS))
            );
        }

        if (configuration.getReadTimeout() != null) {
            final long readTimeoutMillis = configuration.getReadTimeout().toMillis();
            httpClient.doOnConnected(
                    connection -> connection.addHandlerLast(new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS))
            );
        }
    }

    private static SslContext createSslContext(SivaClientConfigurationProperties configuration) {
        try {
            return SslContextBuilder.forClient()
                    .trustManager(createTrustManagerFactory(configuration))
                    .build();
        } catch (SSLException e) {
            throw new IllegalStateException("Failed to build SSL context", e);
        }
    }

    private static TrustManagerFactory createTrustManagerFactory(SivaClientConfigurationProperties configuration) {
        TrustManagerFactory trustManagerFactory;
        try {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(createKeyStore(configuration));
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new TechnicalException("Failed to create trust manager factory", e);
        }

        return trustManagerFactory;
    }

    private static KeyStore createKeyStore(SivaClientConfigurationProperties configuration) {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            throw new TechnicalException("Failed to create a KeyStore instance", e);
        }
        try (InputStream inputStream = configuration.getTrustStore().getInputStream()) {
            keyStore.load(inputStream, configuration.getTrustStorePassword().toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new TechnicalException("Failed to load KeyStore from resource", e);
        }

        return keyStore;
    }
}
