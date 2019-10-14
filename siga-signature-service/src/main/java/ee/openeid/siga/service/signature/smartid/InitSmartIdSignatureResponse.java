package ee.openeid.siga.service.signature.smartid;

import lombok.Data;

@Data
public class InitSmartIdSignatureResponse {
    private String sessionCode;
    private String challengeId;
}
