package ee.openeid.siga.service.signature.configuration;


import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("smartId")
public class SmartIdSigningConfiguration {

    @Bean
    SmartIdSigningDelegate<HashcodeContainerSigningService> hashcodeSmartIdSigningDelegate(
            HashcodeContainerSigningService signingService,
            SmartIdApiClient sobileIdApiClient,
            SmartIdClientConfigurationProperties properties) {
        return new SmartIdSigningDelegate<>(signingService, sobileIdApiClient, properties);
    }

    @Bean
    @Profile("datafileContainer")
    SmartIdSigningDelegate<AsicContainerSigningService> asicSmartIdSigningDelegate(
            AsicContainerSigningService signingService,
            SmartIdApiClient smartIdApiClient,
            SmartIdClientConfigurationProperties properties) {
        return new SmartIdSigningDelegate<>(signingService, smartIdApiClient, properties);
    }
}
