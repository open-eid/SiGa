package ee.openeid.siga.session.configuration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "siga.session-storage.ignite")
public record IgniteSessionProperties(
        @NotNull(message = "siga.session-storage.ignite.configuration-location property must be set")
        Resource configurationLocation) {
}
