package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "siga.dd4j")
@Validated
@Getter
@Setter
public class DigiDoc4jConfigurationProperties {
    @NotNull(message = "siga.dd4j.configuration-location property must be set")
    private Resource configurationLocation;
}
