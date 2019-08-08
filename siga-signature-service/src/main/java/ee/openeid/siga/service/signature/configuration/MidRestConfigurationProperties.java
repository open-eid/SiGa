package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "siga.midrest")
@Validated
@Getter
@Setter
public class MidRestConfigurationProperties {

    private String url;
}
