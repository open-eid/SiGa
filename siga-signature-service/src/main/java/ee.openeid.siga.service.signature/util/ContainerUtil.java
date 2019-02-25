package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashCodeDataFile;


public class ContainerUtil {

    public static HashCodeDataFile transformDataFileToHashCodeDataFile(ee.openeid.siga.webapp.json.HashCodeDataFile dataFile) {
        HashCodeDataFile hashCodeDataFile = new HashCodeDataFile();
        hashCodeDataFile.setFileName(dataFile.getFileName());
        hashCodeDataFile.setFileSize(dataFile.getFileSize());
        hashCodeDataFile.setFileHashSha256(dataFile.getFileHashSha256());
        hashCodeDataFile.setFileHashSha512(dataFile.getFileHashSha512());
        return hashCodeDataFile;
    }
}
