package ee.openeid.siga.test.model;

import lombok.Data;

import static ee.openeid.siga.test.TestData.SERVICE_SECRET;
import static ee.openeid.siga.test.TestData.SERVICE_UUID;

@Data
public class SigaApiFlow {

    private String containerId;

    private String signingTime;
    private String serviceUuid = SERVICE_UUID;
    private String serviceSecret = SERVICE_SECRET;
}
