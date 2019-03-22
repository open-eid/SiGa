package ee.openeid.siga.test.model;

import lombok.Data;

import static ee.openeid.siga.test.TestData.SERVICE_SECRET_1;
import static ee.openeid.siga.test.TestData.SERVICE_UUID_1;

@Data
public class SigaApiFlow {

    private String containerId;

    private String signingTime;
    private String serviceUuid = SERVICE_UUID_1;
    private String serviceSecret = SERVICE_SECRET_1;
}
