package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "siga.siva")
@Validated
@Getter
@Setter
public class SivaClientConfigurationProperties {
    @NotBlank(message = "siga.siva.url property must be set")
    private String url;
    @NotBlank(message = "siga.siva.trust-store property must be set")
    private String trustStore;
    @NotBlank(message = "siga.siva.trust-store-password property must be set")
    private String trustStorePassword;
}
