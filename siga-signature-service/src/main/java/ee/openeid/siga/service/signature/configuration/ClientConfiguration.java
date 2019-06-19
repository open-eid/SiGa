package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
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
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.net.ssl.SSLContext;

@Configuration
@EnableConfigurationProperties({
        SivaConfigurationProperties.class,
        MobileServiceConfigurationProperties.class,
        SmartIdServiceConfigurationProperties.class
})
public class ClientConfiguration {

    private MobileServiceConfigurationProperties mobileServiceConfigurationProperties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    RestTemplate restTemplate() throws Exception {
        CloseableHttpClient httpClient = HttpClients.custom()
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        return new RestTemplate(requestFactory);
    }

    @Bean
    public DigiDocService digiDocService() throws Exception {
        DigiDocService digiDocService = new DigiDocService(mobileServiceConfigurationProperties.getUrlV1());
        digiDocService.setMessageSender(httpComponentsMessageSender());
        return digiDocService;
    }

    @Bean
    public MobileIdService mobileIdService() throws Exception {
        MobileIdService mobileIdService = new MobileIdService(mobileServiceConfigurationProperties.getUrlV2());
        mobileIdService.setMessageSender(httpComponentsMessageSender());
        return mobileIdService;
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
                .loadTrustMaterial(resourceLoader.getResource(mobileServiceConfigurationProperties.getTrustStore()).getFile(),
                        mobileServiceConfigurationProperties.getTrustStorePassword().toCharArray())
                .build();
        return sslcontext;
    }


    @Autowired
    public void setMobileServiceConfigurationProperties(MobileServiceConfigurationProperties mobileServiceConfigurationProperties) {
        this.mobileServiceConfigurationProperties = mobileServiceConfigurationProperties;
    }

}
