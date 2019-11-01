package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SigningChallenge {
    private String challengeId;
    private String generatedSignatureId;
}
