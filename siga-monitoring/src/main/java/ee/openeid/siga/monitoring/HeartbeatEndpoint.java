package ee.openeid.siga.monitoring;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Endpoint(id = "heartbeat", enableByDefault = false)
public class HeartbeatEndpoint {

    @NonNull
    private final HealthEndpoint healthEndpoint;

    @ReadOperation
    public Status heartbeat() {
        return healthEndpoint.health().getStatus();
    }

}
