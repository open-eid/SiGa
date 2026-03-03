package ee.openeid.siga.monitoring;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Endpoint(id = "heartbeat", defaultAccess = Access.NONE)
public class HeartbeatEndpoint {

    @NonNull
    private final HealthEndpoint healthEndpoint;

    @ReadOperation
    public Status heartbeat() {
        return healthEndpoint.health().getStatus();
    }

}
