package ee.openeid.siga.common.model;


import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class SmartIdInformation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String documentNumber;
    private String country;
    private String messageToDisplay;
    private String personIdentifier;
}
