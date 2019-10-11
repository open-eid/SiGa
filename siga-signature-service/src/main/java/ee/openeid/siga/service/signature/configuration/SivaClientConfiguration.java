package ee.openeid.siga.service.signature.configuration;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

@Configuration
@EnableConfigurationProperties({
        SivaClientConfigurationProperties.class
})
public class SivaClientConfiguration {

    private SivaClientConfigurationProperties sivaClientConfigurationProperties;

    private ResourceLoader resourceLoader;

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) throws Exception {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(resourceLoader.getResource(sivaClientConfigurationProperties.getTrustStore()).getFile(), sivaClientConfigurationProperties.getTrustStorePassword().toCharArray())
                .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(socketFactory).build();

        return restTemplateBuilder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient)).build();
    }

    @Autowired
    public void setSivaClientConfigurationProperties(SivaClientConfigurationProperties sivaClientConfigurationProperties) {
        this.sivaClientConfigurationProperties = sivaClientConfigurationProperties;
    }

    @Autowired
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
