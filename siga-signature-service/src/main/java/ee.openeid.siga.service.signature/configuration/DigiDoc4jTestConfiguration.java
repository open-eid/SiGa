package ee.openeid.siga.service.signature.configuration;

import org.digidoc4j.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@Profile("digidoc4jTest")
@EnableConfigurationProperties({
        DigiDoc4jConfigurationProperties.class
})
public class DigiDoc4jTestConfiguration {

    private DigiDoc4jConfigurationProperties dd4jConfigurationProperties;

    @Bean
    public Configuration configuration() {
        Configuration configuration = new Configuration(Configuration.Mode.TEST);
        if (dd4jConfigurationProperties.getTspSource() != null)
            configuration.setTspSource(dd4jConfigurationProperties.getTspSource());
        configuration.setPreferAiaOcsp(true);
        return configuration;
    }

    @Autowired
    public void setDigiDoc4jConfigurationProperties(DigiDoc4jConfigurationProperties dd4jConfigurationProperties) {
        this.dd4jConfigurationProperties = dd4jConfigurationProperties;
    }
}
