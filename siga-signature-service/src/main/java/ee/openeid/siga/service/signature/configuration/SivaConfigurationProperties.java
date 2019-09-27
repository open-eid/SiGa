package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "siga.siva")
@Validated
@Getter
@Setter
public class SivaConfigurationProperties {
    @NotBlank(message = "siga.siva.url property must be set")
    private String url;
}
