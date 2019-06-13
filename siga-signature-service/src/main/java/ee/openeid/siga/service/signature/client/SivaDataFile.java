package ee.openeid.siga.service.signature.client;

import lombok.Data;

@Data
public class SivaDataFile {

    private String filename;
    private String hashAlgo;
    private String hash;
}
