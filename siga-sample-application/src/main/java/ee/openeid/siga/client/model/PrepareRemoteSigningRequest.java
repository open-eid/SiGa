package ee.openeid.siga.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PrepareRemoteSigningRequest extends SigningRequest {
    private byte[] certificate;
}
