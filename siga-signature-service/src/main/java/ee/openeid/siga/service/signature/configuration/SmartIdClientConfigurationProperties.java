package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.smartid.SmartIdInteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "siga.sid")
@Validated
public record SmartIdClientConfigurationProperties(

        @NotNull(message = "siga.sid.truststorePath property must be set")
        Resource truststorePath,

        @NotBlank(message = "siga.sid.truststorePassword property must be set")
        String truststorePassword,

        @NotBlank(message = "siga.sid.url property must be set")
        String url,

        @DefaultValue({"EE", "LT", "LV"})
        List<String> allowedCountries,

        @NotNull(message = "siga.sid.interactionType property must be set")
        @DefaultValue("DISPLAY_TEXT_AND_PIN")
        SmartIdInteractionType interactionType,

        @DefaultValue("5000ms")
        @DurationMin(millis = 1)
        Duration connectTimeout,

        @DefaultValue("30000ms")
        @DurationMin(millis = 1)
        Duration sessionStatusResponseSocketOpenTime,

        @DefaultValue("6000ms")
        @DurationMin(millis = 0)
        Duration statusPollingDelay
) {
}
