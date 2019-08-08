package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "siga.dd4j")
@Validated
@Getter
@Setter
public class DigiDoc4jConfigurationProperties {
    @NotBlank(message = "siga.dd4j.configuration-location property must be set")
    private String configurationLocation;
    private String tspSource;
}
