package ee.openeid.siga.client.hashcode;

import lombok.Data;

@Data
public class SignatureHashcodeDataFile {
    private String fileName;
    private String hashAlgo;
}
