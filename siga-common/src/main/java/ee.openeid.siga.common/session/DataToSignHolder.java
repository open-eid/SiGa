package ee.openeid.siga.common.session;

import ee.openeid.siga.common.SigningType;
import lombok.Builder;
import lombok.Data;
import org.digidoc4j.DataToSign;

@Data
@Builder
public class DataToSignHolder {

    private String sessionCode;
    private DataToSign dataToSign;
    private SigningType signingType;
}
