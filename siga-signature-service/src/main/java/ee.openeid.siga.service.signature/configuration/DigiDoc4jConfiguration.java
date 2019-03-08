package ee.openeid.siga.service.signature.configuration;

import org.digidoc4j.Configuration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;


@SpringBootConfiguration
@Profile("!digidoc4jTest")
public class DigiDoc4jConfiguration {
    @Bean
    public Configuration configuration() {
        return new Configuration();
    }
}
