package ee.openeid.siga.common.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HashcodeSignatureWrapper {
    private String generatedSignatureId;
    private byte[] signature;
    private List<SignatureHashcodeDataFile> dataFiles = new ArrayList<>();
}
