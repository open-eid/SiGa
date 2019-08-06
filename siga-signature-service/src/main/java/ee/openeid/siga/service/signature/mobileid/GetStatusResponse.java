package ee.openeid.siga.service.signature.mobileid;

import lombok.Data;

@Data
public class GetStatusResponse {
    private String status;
    private byte[] signature;
}
