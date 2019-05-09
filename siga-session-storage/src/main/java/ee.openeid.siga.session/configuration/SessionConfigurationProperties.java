package ee.openeid.siga.session.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "siga.ignite")
@Validated
@Getter
@Setter
public class SessionConfigurationProperties {
    @NotBlank(message = "siga.ignite.configuration-location propery must be set")
    private String configurationLocation;
}
