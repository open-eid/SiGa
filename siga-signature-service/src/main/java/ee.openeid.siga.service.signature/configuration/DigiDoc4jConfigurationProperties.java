package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "siga.dd4j")
@Validated
@Getter
@Setter
public class DigiDoc4jConfigurationProperties {
    private String tspSource;
}
