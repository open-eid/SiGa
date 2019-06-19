package ee.openeid.siga.common;


import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class SmartIdInformation {
    private String country;
    private String messageToDisplay;
    private String personIdentifier;

    @NonNull
    private String relyingPartyName;
    @NonNull
    private String relyingPartyUuid;
}
