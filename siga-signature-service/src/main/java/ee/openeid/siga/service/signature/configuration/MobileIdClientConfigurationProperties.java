package ee.openeid.siga.service.signature.configuration;

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
 * {@code siga.midrest.*}. Bound and validated only while the {@code mobileId} profile is active.
 * <p>
 * Fields with a {@link DefaultValue} need no {@code @NotNull}: an empty property value converts to
 * {@code null} and the binder then falls back to the default. An empty list is bound as-is, hence
 * {@code @NotEmpty} on {@link #allowedCountries}.
 */
@ConfigurationProperties(prefix = "siga.midrest")
@Validated
public record MobileIdClientConfigurationProperties(

        @NotNull(message = "siga.midrest.truststorePath property must be set")
        Resource truststorePath,

        @NotBlank(message = "siga.midrest.truststorePassword property must be set")
        String truststorePassword,

        @NotBlank(message = "siga.midrest.url property must be set")
        @URL(message = "siga.midrest.url must be a valid URL")
        String url,

        @DefaultValue({"EE", "LT"})
        @NotEmpty(message = "siga.midrest.allowedCountries must not be empty")
        List<String> allowedCountries,

        @DefaultValue("5000ms")
        @DurationMin(millis = 1)
        Duration connectTimeout,

        @DefaultValue("30000ms")
        @DurationMin(millis = 1000)
        Duration longPollingTimeout,

        @DefaultValue("6000ms")
        @DurationMin(millis = 0)
        Duration statusPollingDelay
) { }
