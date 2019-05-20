package ee.openeid.siga.common;

import lombok.Data;
import org.digidoc4j.Signature;

@Data
public class SignatureWrapper {
    private String generatedSignatureId;
    private Signature signature;
}
