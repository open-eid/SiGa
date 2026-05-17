package ee.openeid.siga.session.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "siga.session-storage")
public record SessionStorageProperties(
        @NotBlank(message = "siga.session-storage.application-cache-version property must be set")
        String applicationCacheVersion) {
}
