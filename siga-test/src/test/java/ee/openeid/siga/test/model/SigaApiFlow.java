package ee.openeid.siga.test.model;

import lombok.Builder;
import lombok.Data;

import static ee.openeid.siga.test.TestData.*;

@Data
@Builder
public class SigaApiFlow {

    private String containerId;

    private String signingTime;
    private String serviceUuid;
    private String serviceSecret;

    @Builder(buildMethodName = "buildForTestClient1Service1")
    public static SigaApiFlow buildForTestClient1Service1() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_1).serviceSecret(SERVICE_SECRET_1).build();
    }

    @Builder(buildMethodName = "buildForTestClient1Service2")
    public static SigaApiFlow buildForTestClient1Service2() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_2).serviceSecret(SERVICE_SECRET_2).build();
    }

    @Builder(buildMethodName = "buildForTestClient2Service3")
    public static SigaApiFlow buildForTestClient2Service3() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_3).serviceSecret(SERVICE_SECRET_3).build();
    }
}
