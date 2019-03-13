package ee.openeid.siga.common;

import lombok.Data;

@Data
public class HashcodeDataFile {

    private String fileName;
    private String fileHashSha256;
    private String fileHashSha512;
    private Integer fileSize;
}
