package ee.openeid.siga.service.signature.mobileid.midrest;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "siga.midrest")
public class MidRestConfigurationProperties {
    @NotBlank(message = "siga.midrest.url property must be set")
    private String url;
}
