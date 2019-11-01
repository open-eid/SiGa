package ee.openeid.siga.common.model;

import lombok.Builder;
import lombok.Data;
import org.digidoc4j.DataToSign;

@Data
@Builder
public class DataToSignWrapper {
    private DataToSign dataToSign;
    private String generatedSignatureId;
}
