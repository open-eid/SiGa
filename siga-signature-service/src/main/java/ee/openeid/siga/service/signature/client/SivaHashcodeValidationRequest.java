package ee.openeid.siga.service.signature.client;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SivaHashcodeValidationRequest {
    private List<SignatureFile> signatureFiles = new ArrayList<>();
}
