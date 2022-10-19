package ee.openeid.siga.service.signature.configuration;

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
@ConfigurationProperties(prefix = "siga.midrest")
public class MobileIdClientConfigurationProperties {

    @Getter(AccessLevel.NONE)
    private final Environment environment;
    private String truststorePath;
    private String truststorePassword;
    private Duration connectTimeout = Duration.ofMillis(5000);
    private Duration longPollingTimeout = Duration.ofMillis(30000);
    private Duration statusPollingDelay = Duration.ofMillis(6000);
    private String url;
    private List<String> allowedCountries = new ArrayList<>(Arrays.asList("EE", "LT"));

    @PostConstruct
    public void validateConfiguration() {
        if (List.of(environment.getActiveProfiles()).contains("mobileId")) {
            validateUrl();
            validateTruststorePath();
            validateTruststorePassword();
        }
    }

    private void validateUrl() {
        if (StringUtils.isBlank(url)) {
            throw new IllegalStateException("siga.midrest.url property must be set");
        }
    }

    private void validateTruststorePath() {
        if (StringUtils.isBlank(truststorePath)) {
            throw new IllegalStateException("siga.midrest.truststorePath property must be set");
        }
    }

    private void validateTruststorePassword() {
        if (StringUtils.isBlank(truststorePassword)) {
            throw new IllegalStateException("siga.midrest.truststorePassword property must be set");
        }
    }
}
