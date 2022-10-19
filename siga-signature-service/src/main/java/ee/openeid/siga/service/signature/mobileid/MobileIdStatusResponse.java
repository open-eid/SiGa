package ee.openeid.siga.service.signature.mobileid;

import lombok.Data;

@Data
public class MobileIdStatusResponse {
    private MobileIdSessionStatus status;
    private byte[] signature;
}
