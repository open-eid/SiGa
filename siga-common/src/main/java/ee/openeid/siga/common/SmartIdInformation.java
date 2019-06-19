package ee.openeid.siga.common;


import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class SmartIdInformation {
    @NonNull
    private String country;
    private String messageToDisplay;
    @NonNull
    private String personIdentifier;
    @NonNull
    private String relyingPartyName;
    @NonNull
    private String relyingPartyUUID;
}
