package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("smartId")
class SmartIdSigningConfiguration {

    @Bean
    SmartIdSigningDelegate<HashcodeContainerSigningService> hashcodeSmartIdSigningDelegate(
            HashcodeContainerSigningService signingService,
            SmartIdApiClient smartIdApiClient,
            SmartIdClientConfigurationProperties properties) {
        return new SmartIdSigningDelegate<>(signingService, smartIdApiClient, properties);
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
