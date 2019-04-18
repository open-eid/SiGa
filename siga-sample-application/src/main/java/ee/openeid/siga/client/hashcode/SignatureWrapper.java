package ee.openeid.siga.client.hashcode;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SignatureWrapper {
    private String generatedSignatureId;
    private byte[] signature;
    private List<SignatureHashcodeDataFile> dataFiles = new ArrayList<>();
}
