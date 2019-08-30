package ee.openeid.siga.service.signature.configuration;

import org.digidoc4j.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;


@SpringBootConfiguration
@Profile({"digidoc4jProd"})
@EnableConfigurationProperties({
        DigiDoc4jConfigurationProperties.class
})
public class DigiDoc4jConfiguration {

    private DigiDoc4jConfigurationProperties dd4jConfigurationProperties;

    @Bean
    public Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.loadConfiguration(dd4jConfigurationProperties.getConfigurationLocation());
        configuration.setPreferAiaOcsp(true);
        return configuration;
    }

    @Autowired
    public void setDigiDoc4jConfigurationProperties(DigiDoc4jConfigurationProperties dd4jConfigurationProperties) {
        this.dd4jConfigurationProperties = dd4jConfigurationProperties;
    }
}
