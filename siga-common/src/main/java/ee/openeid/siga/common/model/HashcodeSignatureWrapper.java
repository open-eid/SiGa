package ee.openeid.siga.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class HashcodeSignatureWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    private String generatedSignatureId;
    private byte[] signature;
    private List<SignatureHashcodeDataFile> dataFiles = new ArrayList<>();
}
