package ee.openeid.siga.service.signature.mobileid;

import lombok.Data;

@Data
public class GetStatusResponse {
    private MidStatus status;
    private byte[] signature;
}
