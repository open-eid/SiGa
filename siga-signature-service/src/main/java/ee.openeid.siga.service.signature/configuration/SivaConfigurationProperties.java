package ee.openeid.siga.service.signature.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "siga.siva")
public class SivaConfigurationProperties {
    private String url;
}
