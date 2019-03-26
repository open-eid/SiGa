package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.mobileid.client.DdsService;
import ee.openeid.siga.mobileid.client.MidService;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.net.ssl.SSLContext;

@Configuration
@EnableConfigurationProperties({
        SivaConfigurationProperties.class,
        MobileServiceConfigurationProperties.class
})
public class ClientConfiguration {

    private MobileServiceConfigurationProperties mobileServiceConfigurationProperties;

    @Bean
    RestTemplate restTemplate() throws Exception {
        CloseableHttpClient httpClient = HttpClients.custom()
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        return new RestTemplate(requestFactory);
    }

    @Bean
    public DdsService ddsService() throws Exception {
        DdsService ddsService = new DdsService(mobileServiceConfigurationProperties.getUrlV1());
        ddsService.setMessageSender(httpComponentsMessageSender());
        return ddsService;
    }

    @Bean
    public MidService midService() throws Exception {
        MidService midService = new MidService(mobileServiceConfigurationProperties.getUrlV2());
        midService.setMessageSender(httpComponentsMessageSender());
        return midService;
    }

    @Bean
    public HttpComponentsMessageSender httpComponentsMessageSender() throws Exception {
        return new HttpComponentsMessageSender(httpClient());
    }

    private HttpClient httpClient() throws Exception {
        return HttpClientBuilder.create().setSSLSocketFactory(sslConnectionSocketFactory())
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor()).build();
    }

    private SSLConnectionSocketFactory sslConnectionSocketFactory() throws Exception {
        return new SSLConnectionSocketFactory(sslContext());
    }


    private SSLContext sslContext() throws Exception {
        SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(new ClassPathResource(mobileServiceConfigurationProperties.getTrustStore()).getFile(),
                        mobileServiceConfigurationProperties.getTrustStorePassword().toCharArray())
                .build();
        return sslcontext;
    }

    @Autowired
    public void setMobileServiceConfigurationProperties(MobileServiceConfigurationProperties mobileServiceConfigurationProperties) {
        this.mobileServiceConfigurationProperties = mobileServiceConfigurationProperties;
    }
}
