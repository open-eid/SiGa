package ee.openeid.siga.common.model;


import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class SmartIdInformation {
    private String documentNumber;
    private String country;
    private String messageToDisplay;
    private String personIdentifier;
}
