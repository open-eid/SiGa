package ee.openeid.siga.service.signature.configuration;

import org.digidoc4j.Configuration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@Profile({"datafileContainer"})
public class DigiDoc4jEuConfiguration {

    @Bean
    public Configuration euConfiguration(Configuration configuration) {
        Configuration euConfiguration = configuration.copy();
        euConfiguration.setValidationPolicy("dss-constraint.xml");
        return euConfiguration;
    }
}
