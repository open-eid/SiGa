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
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "siga.sid")
@Getter
@Setter
@RequiredArgsConstructor
public class SmartIdServiceConfigurationProperties {

    @Getter(AccessLevel.NONE)
    private final Environment environment;

    private String url;

    private int sessionStatusResponseSocketOpenTime = 40;

    @PostConstruct
    public void validateConfiguration() {
        if (List.of(environment.getActiveProfiles()).contains("smartId")) {
            validateUrl();
        }
    }

    private void validateUrl() {
        if (StringUtils.isBlank(url)) {
            throw new IllegalStateException("siga.sid.url property must be set");
        }
    }
}
