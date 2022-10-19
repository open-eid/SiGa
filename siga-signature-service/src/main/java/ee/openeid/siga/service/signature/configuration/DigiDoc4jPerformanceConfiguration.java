package ee.openeid.siga.service.signature.configuration;

import lombok.RequiredArgsConstructor;
import org.digidoc4j.Configuration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootConfiguration
@Profile("digidoc4jPerf")
@EnableConfigurationProperties({DigiDoc4jConfigurationProperties.class})
@RequiredArgsConstructor
public class DigiDoc4jPerformanceConfiguration {
    private final DigiDoc4jConfigurationProperties dd4jConfigurationProperties;

    @Bean
    public Configuration configuration() {
        Configuration configuration = new Configuration(Configuration.Mode.TEST);
        configuration.loadConfiguration(dd4jConfigurationProperties.getConfigurationLocation());
        return configuration;
    }
}
