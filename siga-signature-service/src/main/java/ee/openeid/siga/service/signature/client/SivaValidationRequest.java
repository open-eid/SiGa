package ee.openeid.siga.service.signature.client;

import lombok.Data;

import java.util.List;

@Data
public class SivaValidationRequest {
    private String signatureFile;
    private String filename;
    private List<SivaDataFile> datafiles;
}
