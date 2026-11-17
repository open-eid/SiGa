package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mobileId")
class MobileIdSigningConfiguration {

    @Bean
    MobileIdSigningDelegate<HashcodeContainerSigningService> hashcodeMobileIdSigningDelegate(
            HashcodeContainerSigningService signingService,
            MobileIdApiClient mobileIdApiClient,
            MobileIdClientConfigurationProperties properties) {
        return new MobileIdSigningDelegate<>(signingService, mobileIdApiClient, properties);
    }

    @Bean
    @Profile("datafileContainer")
    MobileIdSigningDelegate<AsicContainerSigningService> asicMobileIdSigningDelegate(
            AsicContainerSigningService signingService,
            MobileIdApiClient mobileIdApiClient,
            MobileIdClientConfigurationProperties properties) {
        return new MobileIdSigningDelegate<>(signingService, mobileIdApiClient, properties);
    }
}
