package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "siga.sid")
@Validated
@Getter
@Setter
public class SmartIdServiceConfigurationProperties {

    @NotBlank(message = "siga.sid.url property must be set")
    private String url;
}
