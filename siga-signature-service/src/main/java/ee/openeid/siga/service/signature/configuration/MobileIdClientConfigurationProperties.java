package ee.openeid.siga.service.signature.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "siga.midrest")
@Validated
public record MobileIdClientConfigurationProperties(

        @NotNull(message = "siga.midrest.truststorePath property must be set")
        Resource truststorePath,

        @NotBlank(message = "siga.midrest.truststorePassword property must be set")
        String truststorePassword,

        @NotBlank(message = "siga.midrest.url property must be set")
        String url,

        @DefaultValue({"EE", "LT"})
        @NotNull
        List<String> allowedCountries,

        @DefaultValue("5000ms")
        @DurationMin(millis = 1)
        @NotNull
        Duration connectTimeout,

        @DefaultValue("30000ms")
        @DurationMin(millis = 1)
        Duration longPollingTimeout,

        @DefaultValue("6000ms")
        @DurationMin(millis = 0)
        Duration statusPollingDelay
) { }
