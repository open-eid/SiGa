package ee.openeid.siga.common;

import lombok.Data;

@Data
public class Signature {
    protected String id;
    protected String generatedSignatureId;
    protected String signerInfo;
    protected String signatureProfile;
}
