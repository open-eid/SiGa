package ee.openeid.siga.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinalizeRemoteSigningRequest extends SigningRequest {
    private String signatureId;
    private byte[] signature;
}
