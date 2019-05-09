package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "siga.dds")
@Validated
@Getter
@Setter
public class MobileServiceConfigurationProperties {
    @NotBlank(message = "siga.dds.url-v1 propery must be set")
    private String urlV1;
    @NotBlank(message = "siga.dds.url-v2 propery must be set")
    private String urlV2;
    @NotBlank(message = "siga.dds.trust-store propery must be set")
    private String trustStore;
    @NotBlank(message = "siga.dds.trust-store-password propery must be set")
    private String trustStorePassword;
}
