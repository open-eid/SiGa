package ee.openeid.siga.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MobileIdChallenge {

    private String challengeId;
    private String generatedSignatureId;
}
