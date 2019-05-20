package ee.openeid.siga.service.signature.util;

import ee.openeid.siga.common.HashcodeSignatureWrapper;
import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.SignatureHashcodeDataFile;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;

import java.util.Map;


public class ContainerUtil {

    public static Signature transformSignature(HashcodeSignatureWrapper signatureWrapper) {
        Signature signature = new Signature();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(new Configuration());
        org.digidoc4j.Signature dd4jSignature = builder.openAdESSignature(signatureWrapper.getSignature());
        signature.setId(dd4jSignature.getId());
        signature.setGeneratedSignatureId(signatureWrapper.getGeneratedSignatureId());
        signature.setSignatureProfile(dd4jSignature.getProfile().name());
        signature.setSignerInfo(dd4jSignature.getSigningCertificate().getSubjectName());
        return signature;
    }

    public static void addSignatureDataFilesEntries(HashcodeSignatureWrapper wrapper, Map<String, String> dataFiles) {
        dataFiles.forEach((fileName, fileHashAlgo) -> {
            SignatureHashcodeDataFile hashcodeDataFile = new SignatureHashcodeDataFile();
            hashcodeDataFile.setFileName(fileName);
            hashcodeDataFile.setHashAlgo(fileHashAlgo);
            wrapper.getDataFiles().add(hashcodeDataFile);
        });
    }
}
