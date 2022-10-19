package ee.openeid.siga.service.signature.configuration;

import ee.openeid.siga.service.signature.smartid.SmartIdInteractionType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "siga.sid")
public class SmartIdClientConfigurationProperties {
    @Getter(AccessLevel.NONE)
    private final Environment environment;
    private String truststorePath;
    private String truststorePassword;
    private String url;
    private List<String> allowedCountries = new ArrayList<>(Arrays.asList("EE", "LT", "LV"));
    private SmartIdInteractionType interactionType = SmartIdInteractionType.DISPLAY_TEXT_AND_PIN;
    private Duration connectTimeout = Duration.ofMillis(5000);
    private Duration sessionStatusResponseSocketOpenTime = Duration.ofMillis(30000);
    private Duration statusPollingDelay = Duration.ofMillis(6000);

    @PostConstruct
    public void validateConfiguration() {
        if (List.of(environment.getActiveProfiles()).contains("smartId")) {
            validateUrl();
            validateTruststorePath();
            validateTruststorePassword();
            validateInteractionType();
        }
    }

    private void validateUrl() {
        if (StringUtils.isBlank(url)) {
            throw new IllegalStateException("siga.sid.url property must be set");
        }
    }

    private void validateTruststorePath() {
        if (StringUtils.isBlank(truststorePath)) {
            throw new IllegalStateException("siga.sid.truststorePath property must be set");
        }
    }

    private void validateTruststorePassword() {
        if (StringUtils.isBlank(truststorePassword)) {
            throw new IllegalStateException("siga.sid.truststorePassword property must be set");
        }
    }

    private void validateInteractionType() {
        if (interactionType == null) {
            throw new IllegalStateException("siga.sid.interactionType property must be set");
        }
    }
}
