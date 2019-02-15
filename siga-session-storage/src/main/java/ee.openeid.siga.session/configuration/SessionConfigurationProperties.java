package ee.openeid.siga.session.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "siga.ignite.configuration")
public class SessionConfigurationProperties {
    private String location;
    private int expiryDuration;
}
