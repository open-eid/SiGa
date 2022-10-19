package ee.openeid.siga.service.signature.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "siga.status-reprocessing")
public class SessionStatusReprocessingProperties {
    private Integer maxProcessingAttempts = 10;
    private Duration processingTimeout = Duration.ofMillis(30000);
    private Duration exceptionTimeout = Duration.ofMillis(5000);
}
