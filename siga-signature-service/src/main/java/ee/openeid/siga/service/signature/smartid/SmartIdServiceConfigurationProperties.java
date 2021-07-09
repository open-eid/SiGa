package ee.openeid.siga.service.signature.smartid;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "siga.sid")
@Getter
@Setter
@RequiredArgsConstructor
public class SmartIdServiceConfigurationProperties {

    @Getter(AccessLevel.NONE)
    private final Environment environment;
    private String truststorePath;
    private String truststorePassword;
    private String url;
    private List<String> allowedCountries = new ArrayList<>(Arrays.asList("EE", "LT", "LV"));
    private SmartIdInteractionType interactionType = SmartIdInteractionType.DISPLAY_TEXT_AND_PIN;

    private int sessionStatusResponseSocketOpenTime = 1000;

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

    private void validateTruststorePath(){
        if (StringUtils.isBlank(truststorePath)) {
            throw new IllegalStateException("siga.sid.truststorePath property must be set");
        }
    }

    private void validateTruststorePassword(){
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
