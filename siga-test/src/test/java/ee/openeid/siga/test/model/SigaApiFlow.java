package ee.openeid.siga.test.model;

import io.restassured.response.Response;
import lombok.Builder;
import lombok.Data;

import static ee.openeid.siga.test.helper.TestData.*;

@Data
@Builder
public class SigaApiFlow {

    private String containerId;

    private String signingTime;
    private Boolean forceSigningTime;
    private Response midStatus;
    private Response sidStatus;
    private Response sidCertificateStatus;

    private String serviceUuid;
    private String serviceSecret;
    private String hmacAlgorithm;

    @Builder(buildMethodName = "buildForTestClient1Service1")
    public static SigaApiFlow buildForTestClient1Service1() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_1).serviceSecret(SERVICE_SECRET_1).forceSigningTime(false).hmacAlgorithm("HmacSHA256").build();
    }

    @Builder(buildMethodName = "buildForTestClient1Service2")
    public static SigaApiFlow buildForTestClient1Service2() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_2).serviceSecret(SERVICE_SECRET_2).forceSigningTime(false).hmacAlgorithm("HmacSHA256").build();
    }

    @Builder(buildMethodName = "buildForTestClient2Service3")
    public static SigaApiFlow buildForTestClient2Service3() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_3).serviceSecret(SERVICE_SECRET_3).forceSigningTime(false).hmacAlgorithm("HmacSHA256").build();
    }

    @Builder(buildMethodName = "buildForTestClient2Service5")
    public static SigaApiFlow buildForTestClient2Service5() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_5).serviceSecret(SERVICE_SECRET_5).forceSigningTime(false).hmacAlgorithm("HmacSHA256").build();
    }

    @Builder(buildMethodName = "buildForTestClient2Service7")
    public static SigaApiFlow buildForTestClient2Service7() {
        return SigaApiFlow.builder().serviceUuid(SERVICE_UUID_7).serviceSecret(SERVICE_SECRET_7).forceSigningTime(false).hmacAlgorithm("HmacSHA256").build();
    }
}
