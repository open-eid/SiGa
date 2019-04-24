package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureHashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.webapp.json.Signature;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;

import java.util.Map;


public class ContainerUtil {

    public static HashcodeDataFile transformDataFileToHashcodeDataFile(ee.openeid.siga.webapp.json.HashcodeDataFile dataFile) {
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName(dataFile.getFileName());
        hashcodeDataFile.setFileSize(dataFile.getFileSize());
        hashcodeDataFile.setFileHashSha256(dataFile.getFileHashSha256());
        hashcodeDataFile.setFileHashSha512(dataFile.getFileHashSha512());
        return hashcodeDataFile;
    }

    public static Signature transformSignature(SignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(new Configuration());
        org.digidoc4j.Signature dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        signature.setId(dd4jSignature.getId());
        signature.setGeneratedSignatureId(signatureWrapper.getGeneratedSignatureId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    public static void addSignatureDataFilesEntries(SignatureWrapper wrapper, Map<String, String> dataFiles) {
        dataFiles.forEach((fileName, fileHashAlgo) -> {
            SignatureHashcodeDataFile hashcodeDataFile = new SignatureHashcodeDataFile();
            hashcodeDataFile.setFileName(fileName);
            hashcodeDataFile.setHashAlgo(fileHashAlgo);
            wrapper.getDataFiles().add(hashcodeDataFile);
        });
    }
}
