package ee.openeid.siga.client.hashcode;

import lombok.Data;

@Data
public class HashcodeDataFile {

    private String fileName;
    private String fileHashSha256;
    private String fileHashSha512;
    private Integer fileSize;
}
