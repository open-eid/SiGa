package ee.openeid.siga.service.signature.client;

import lombok.Data;

import java.util.List;

@Data
public class SignatureFile {
    private String signature;
    private List<SivaDataFile> datafiles;
}
