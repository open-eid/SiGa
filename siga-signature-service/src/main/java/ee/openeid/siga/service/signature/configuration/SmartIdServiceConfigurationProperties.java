package ee.openeid.siga.service.signature.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "siga.sid")
@Validated
@Getter
@Setter
@Profile("smartId")
public class SmartIdServiceConfigurationProperties {

    @NotBlank(message = "siga.sid.url property must be set")
    private String url;
    @NotNull(message = "siga.sid.session-status-response-socket-open-time property must be set")
    private int sessionStatusResponseSocketOpenTime;
}
