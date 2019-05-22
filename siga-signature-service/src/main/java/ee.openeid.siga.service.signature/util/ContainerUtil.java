package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashcodeSignatureWrapper;
import ee.openeid.siga.common.SignatureHashcodeDataFile;

import java.util.Map;


public class ContainerUtil {

    public static void addSignatureDataFilesEntries(HashcodeSignatureWrapper wrapper, Map<String, String> dataFiles) {
        dataFiles.forEach((fileName, fileHashAlgo) -> {
            SignatureHashcodeDataFile hashcodeDataFile = new SignatureHashcodeDataFile();
            hashcodeDataFile.setFileName(fileName);
            hashcodeDataFile.setHashAlgo(fileHashAlgo);
            wrapper.getDataFiles().add(hashcodeDataFile);
        });
    }
}
