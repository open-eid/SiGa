package ee.openeid.siga.monitoring;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class HeartbeatEndpointTest {

    @Mock
    private HealthEndpoint healthEndpoint;
    @InjectMocks
    private HeartbeatEndpoint heartbeatEndpoint;

    @Mock
    private HealthComponent healthComponent;

    @ParameterizedTest
    @MethodSource("allStatuses")
    void testHealthEndpointIsPolledForStatus(Status status) {
        mockHealthEndpointToReturnStatus(status);

        Status returnedStatus = heartbeatEndpoint.heartbeat();

        Assertions.assertSame(status, returnedStatus);
        assertHealthEndpointReturnedStatus(Mockito.times(1));
    }

    @ParameterizedTest
    @MethodSource("combinationsOfAllStatusesTwice")
    void testHealthEndpointIsPolledSecondTime(Status status1, Status status2) {
        mockHealthEndpointToReturnStatuses(status1, status2);

        Status returnedStatus1 = heartbeatEndpoint.heartbeat();

        Assertions.assertSame(status1, returnedStatus1);
        assertHealthEndpointReturnedStatus(Mockito.times(1));

        Status returnedStatus2 = heartbeatEndpoint.heartbeat();

        Assertions.assertSame(status2, returnedStatus2);
        assertHealthEndpointReturnedStatus(Mockito.times(2));
    }

    private static Stream<Arguments> combinationsOfAllStatusesTwice() {
        return allStatuses()
                .flatMap(status1 -> allStatuses()
                        .map(status2 -> Arguments.of(status1, status2))
                );
    }

    private void mockHealthEndpointToReturnStatus(Status status) {
        Mockito.doReturn(healthComponent).when(healthEndpoint).health();
        Mockito.doReturn(status).when(healthComponent).getStatus();
    }

    private void mockHealthEndpointToReturnStatuses(Status... statuses) {
        Object firstStatus = statuses[0];
        Object[] nextStatuses = new Object[statuses.length - 1];
        System.arraycopy(statuses, 1, nextStatuses, 0, nextStatuses.length);
        Mockito.doReturn(firstStatus, nextStatuses).when(healthComponent).getStatus();
        Mockito.doReturn(healthComponent).when(healthEndpoint).health();
    }

    private void assertHealthEndpointReturnedStatus(VerificationMode verificationMode) {
        Mockito.verify(healthEndpoint, verificationMode).health();
        Mockito.verify(healthComponent, verificationMode).getStatus();
        Mockito.verifyNoMoreInteractions(healthEndpoint, healthComponent);
    }

    private static Stream<Status> allStatuses() {
        return Stream.of(
                Status.DOWN,
                Status.OUT_OF_SERVICE,
                Status.UNKNOWN,
                Status.UP
        );
    }

}
