package ee.openeid.siga.session.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter

@Validated
@ConfigurationProperties(prefix = "siga.ignite")
public class SessionConfigurationProperties {
    @NotBlank(message = "siga.ignite.configuration-location propery must be set")
    private String configurationLocation;
    @NotBlank(message = "siga.ignite.application-cache-version propery must be set")
    private String applicationCacheVersion;
}
