package ee.openeid.siga.service.signature.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "siga.dd4j")
public class DigiDoc4jConfigurationProperties {
    private String tspSource;
}
