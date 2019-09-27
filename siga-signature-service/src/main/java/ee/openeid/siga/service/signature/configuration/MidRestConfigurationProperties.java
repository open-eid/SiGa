package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "siga.midrest")
@Validated
@Getter
@Setter
@Profile("midRest")
public class MidRestConfigurationProperties {
    @NotBlank(message = "siga.midrest.url property must be set")
    private String url;
}
