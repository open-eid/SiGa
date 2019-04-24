package ee.openeid.siga.client.hashcode.util;

import ee.openeid.siga.client.hashcode.SignatureHashcodeDataFile;
import ee.openeid.siga.client.hashcode.SignatureWrapper;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import lombok.SneakyThrows;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;


public class ContainerUtil {

    @SneakyThrows
    public static HashcodeDataFile createHashcodeDataFile(String fileName, long fileSize, byte[] fileContent) {
        ee.openeid.siga.webapp.json.HashcodeDataFile df = new ee.openeid.siga.webapp.json.HashcodeDataFile();
        df.setFileName(fileName);
        df.setFileSize(Math.toIntExact(fileSize));
        MessageDigest digest256 = MessageDigest.getInstance("SHA-256");
        byte[] hash256 = digest256.digest(fileContent);
        MessageDigest digest512 = MessageDigest.getInstance("SHA-512");
        byte[] hash512 = digest512.digest(fileContent);
        df.setFileHashSha256(Base64.getEncoder().encodeToString(hash256));
        df.setFileHashSha512(Base64.getEncoder().encodeToString(hash512));
        return df;
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
