package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.service.signature.mobileid.DigiDocServiceClient;
import ee.openeid.siga.service.signature.mobileid.MidRestClient;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.net.ssl.SSLContext;

@Configuration
public class ProfiledMobileIdConfiguration {

    @Bean
    @Profile("midRest")
    public MobileIdClient midrestClient() {
        return new MidRestClient();
    }

    @Bean
    @Profile("!midRest")
    public MobileIdClient digidocServiceClient() {
        return new DigiDocServiceClient();
    }

    @Configuration
    @Profile("!midRest")
    @RequiredArgsConstructor
    public static class MobileServiceConfiguration {

        private final ResourceLoader resourceLoader;

        private final MobileServiceConfigurationProperties mobileServiceConfigurationProperties;

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

    }

}
