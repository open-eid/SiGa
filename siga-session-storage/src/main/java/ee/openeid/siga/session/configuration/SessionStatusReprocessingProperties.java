package ee.openeid.siga.session.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Shared configuration for failed signature/certificate status reprocessing. Lives in
 * {@code siga-session-storage} so Redis status index maintenance can apply
 * {@code maxProcessingAttempts} before retry-exhausted sessions occupy the bounded scanner page,
 * while the {@code SessionStatusReprocessingService} in {@code siga-signature-service} consumes the
 * same bean for retry-counter and timeout decisions.
 */
@Validated
@ConfigurationProperties(prefix = "siga.status-reprocessing")
public record SessionStatusReprocessingProperties(
        @DefaultValue("10") @Positive int maxProcessingAttempts,
        @DefaultValue("30s") Duration processingTimeout,
        @DefaultValue("5s") Duration exceptionTimeout) {

    /**
     * Instance populated with the same values Spring binds when no {@code siga.status-reprocessing.*}
     * properties are set. Intended for tests that exercise the default configuration.
     */
    public static SessionStatusReprocessingProperties withDefaults() {
        return new SessionStatusReprocessingProperties(10, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }
}
