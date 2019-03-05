package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.SignatureHashCodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;

import java.util.Map;


public class ContainerUtil {

    public static HashCodeDataFile transformDataFileToHashCodeDataFile(ee.openeid.siga.webapp.json.HashCodeDataFile dataFile) {
        HashCodeDataFile hashCodeDataFile = new HashCodeDataFile();
        hashCodeDataFile.setFileName(dataFile.getFileName());
        hashCodeDataFile.setFileSize(dataFile.getFileSize());
        hashCodeDataFile.setFileHashSha256(dataFile.getFileHashSha256());
        hashCodeDataFile.setFileHashSha512(dataFile.getFileHashSha512());
        return hashCodeDataFile;
    }

    public static void addSignatureDataFilesEntries(SignatureWrapper wrapper, Map<String, String> dataFiles) {
        dataFiles.forEach((fileName, fileHashAlgo) -> {
            SignatureHashCodeDataFile hashCodeDataFile = new SignatureHashCodeDataFile();
            hashCodeDataFile.setFileName(fileName);
            hashCodeDataFile.setHashAlgo(fileHashAlgo);
            wrapper.getDataFiles().add(hashCodeDataFile);
        });
    }
}
