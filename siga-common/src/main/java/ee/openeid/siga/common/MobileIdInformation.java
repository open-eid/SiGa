package ee.openeid.siga.common;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class MobileIdInformation {
    private String personIdentifier;
    private String phoneNo;
    private String language;
    private String messageToDisplay;
    @NonNull
    private String relyingPartyName;
    private String relyingPartyUUID;
}
