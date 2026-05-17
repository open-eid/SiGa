package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class MobileIdInformation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String personIdentifier;
    private String phoneNo;
    private String language;
    private String messageToDisplay;
}
