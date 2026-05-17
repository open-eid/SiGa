package ee.openeid.siga.common.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class SignatureHashcodeDataFile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;
    private String hashAlgo;
}
