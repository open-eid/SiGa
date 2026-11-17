package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.smartid.SmartIdInteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code siga.sid.*}. Bound and validated only while the {@code smartId} profile is active.
 * <p>
 * Fields with a {@link DefaultValue} need no {@code @NotNull}: an empty property value converts to
 * {@code null} and the binder then falls back to the default. An empty list is bound as-is, hence
 * {@code @NotEmpty} on {@link #allowedCountries}.
 */
@ConfigurationProperties(prefix = "siga.sid")
@Validated
public record SmartIdClientConfigurationProperties(

        @NotNull(message = "siga.sid.truststorePath property must be set")
        Resource truststorePath,

        @NotBlank(message = "siga.sid.truststorePassword property must be set")
        String truststorePassword,

        @NotBlank(message = "siga.sid.url property must be set")
        @URL(message = "siga.sid.url must be a valid URL")
        String url,

        @DefaultValue({"EE", "LT", "LV"})
        @NotEmpty(message = "siga.sid.allowedCountries must not be empty")
        List<String> allowedCountries,

        @DefaultValue("DISPLAY_TEXT_AND_PIN")
        SmartIdInteractionType interactionType,

        @DefaultValue("5000ms")
        @DurationMin(millis = 1)
        Duration connectTimeout,

        @DefaultValue("30000ms")
        @DurationMin(millis = 1000)
        Duration sessionStatusResponseSocketOpenTime,

        @DefaultValue("6000ms")
        @DurationMin(millis = 0)
        Duration statusPollingDelay
) { }
