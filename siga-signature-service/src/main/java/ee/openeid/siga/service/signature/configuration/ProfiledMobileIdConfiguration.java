package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.mobileid.DigiDocServiceClient;
import ee.openeid.siga.service.signature.mobileid.MidRestClient;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
}
