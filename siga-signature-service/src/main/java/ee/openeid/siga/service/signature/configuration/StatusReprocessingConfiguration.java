package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionStatusReprocessingProperties.class)
public class StatusReprocessingConfiguration {
}
