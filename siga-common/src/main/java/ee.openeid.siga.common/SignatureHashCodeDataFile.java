package ee.openeid.siga.common;

import lombok.Data;

@Data
public class SignatureHashCodeDataFile {
    private String fileName;
    private String hashAlgo;
}
