package ee.openeid.siga.client.hashcode;

import lombok.Data;

@Data
public class HashcodeDataFile {

    private String fileName;
    private String fileHashSha256;
    private String fileHashSha512;
    private String mimeType;
    private Integer fileSize;


    public ee.openeid.siga.webapp.json.HashcodeDataFile convertToRequest() {
        ee.openeid.siga.webapp.json.HashcodeDataFile hashcodeDataFile = new ee.openeid.siga.webapp.json.HashcodeDataFile();
        hashcodeDataFile.setFileName(fileName);
        hashcodeDataFile.setFileSize(fileSize);
        hashcodeDataFile.setFileHashSha256(fileHashSha256);
        hashcodeDataFile.setFileHashSha512(fileHashSha512);
        return hashcodeDataFile;
    }
}
