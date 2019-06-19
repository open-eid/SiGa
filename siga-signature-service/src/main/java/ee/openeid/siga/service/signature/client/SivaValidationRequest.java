package ee.openeid.siga.service.signature.client;

import lombok.Data;

@Data
public class SivaValidationRequest {
    private String document;
    private String filename;
}
