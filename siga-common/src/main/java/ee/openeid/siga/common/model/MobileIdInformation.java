package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MobileIdInformation {
    private String personIdentifier;
    private String phoneNo;
    private String language;
    private String messageToDisplay;
    private String relyingPartyName;
    private String relyingPartyUUID;
}
