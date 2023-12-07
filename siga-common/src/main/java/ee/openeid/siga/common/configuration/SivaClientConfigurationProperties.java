package ee.openeid.siga.common.configuration;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

@ConfigurationProperties(prefix = "siga.siva")
@Validated
@Getter
@Setter
public class SivaClientConfigurationProperties {
    @NotBlank(message = "siga.siva.url property must be set")
    private String url;
    @NotNull(message = "siga.siva.trust-store property must be set")
    private Resource trustStore;
    @NotBlank(message = "siga.siva.trust-store-password property must be set")
    private String trustStorePassword;
    @DurationMin(message = "duration must not be negative")
    @DurationMax(millis = Integer.MAX_VALUE)
    private Duration connectionTimeout;
    @DurationMin(message = "duration must not be negative")
    private Duration writeTimeout;
    @DurationMin(message = "duration must not be negative")
    private Duration readTimeout;
}
