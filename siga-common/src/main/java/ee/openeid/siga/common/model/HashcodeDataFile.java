package ee.openeid.siga.common.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class HashcodeDataFile implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileName;
    private String fileHashSha256;
    private String fileHashSha512;
    private String mimeType;
    private Integer fileSize;
}
