package ee.openeid.siga.common.model;

import lombok.Data;

@Data
public class SignatureHashcodeDataFile {
    private String fileName;
    private String hashAlgo;
}
