package ee.openeid.siga.common;

import lombok.Data;

@Data
public class MobileIdInformation {

    private String personIdentifier;
    private String phoneNo;
    private String language;
    private String country;
    private String messageToDisplay;
}
