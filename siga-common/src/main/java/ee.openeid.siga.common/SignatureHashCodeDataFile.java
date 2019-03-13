package ee.openeid.siga.common;

import lombok.Data;

@Data
public class SignatureHashcodeDataFile {
    private String fileName;
    private String hashAlgo;
}
