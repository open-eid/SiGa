package ee.openeid.siga.common.model;

import lombok.Data;

@Data
public class HashcodeDataFile {

    private String fileName;
    private String fileHashSha256;
    private String fileHashSha512;
    private String mimeType;
    private Integer fileSize;
}
