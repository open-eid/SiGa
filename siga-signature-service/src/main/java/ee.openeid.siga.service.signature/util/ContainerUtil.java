package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.webapp.json.DataFile;

public class ContainerUtil {

    public static HashCodeDataFile transformDataFileToHashCodeDataFile(DataFile dataFile) {
        HashCodeDataFile hashCodeDataFile = new HashCodeDataFile();
        hashCodeDataFile.setFileName(dataFile.getFileName());
        hashCodeDataFile.setFileSize(dataFile.getFileSize());
        hashCodeDataFile.setFileHashSha256(dataFile.getFileHashSha256());
        hashCodeDataFile.setFileHashSha512(dataFile.getFileHashSha512());
        return hashCodeDataFile;
    }
}
