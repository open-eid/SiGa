package ee.openeid.siga.service.signature.mobileid;

import lombok.Data;

@Data
public class InitMidSignatureResponse {
    private String sessionCode;
    private String challengeId;
}
