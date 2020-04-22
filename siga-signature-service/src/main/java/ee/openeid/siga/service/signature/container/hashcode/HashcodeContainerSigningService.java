package ee.openeid.siga.service.signature.container.hashcode;


import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.common.session.HashcodeContainerSessionHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.hashcode.SignatureDataFilesParser;
import ee.openeid.siga.service.signature.session.HashcodeSessionHolder;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.DigestDataFile;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.exceptions.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HashcodeContainerSigningService extends ContainerSigningService implements HashcodeSessionHolder {

    private Configuration configuration;

    @Override
    public DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters) {
        HashcodeContainerSessionHolder sessionHolder = (HashcodeContainerSessionHolder) session;
        DetachedXadesSignatureBuilder signatureBuilder = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters);
        return signatureBuilder.buildDataToSign();
    }

    @Override
    public Session getSession(String containerId) {
        return getSessionHolder(containerId);
    }

    @Override
    public void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId) {
        HashcodeSignatureWrapper signatureWrapper = createSignatureWrapper(signatureId, signature.getAdESSignature());
        HashcodeContainerSessionHolder hashcodeContainerSessionHolder = (HashcodeContainerSessionHolder) sessionHolder;
        hashcodeContainerSessionHolder.getSignatures().add(signatureWrapper);
        hashcodeContainerSessionHolder.clearSigning(signatureId);
    }

    @Override
    public void verifySigningObjectExistence(Session session) {
        verifyDataFileExistence((HashcodeContainerSessionHolder) session);
    }

    @Override
    public String generateDataFilesHash(Session session) {
        String joinedDataFiles = ((HashcodeContainerSessionHolder) session).getDataFiles().stream()
                .sorted(Comparator.comparing(HashcodeDataFile::getFileName))
                .map(dataFile -> dataFile.getFileName() + dataFile.getFileHashSha256())
                .collect(Collectors.joining());
        return new String(DigestUtils.sha256(joinedDataFiles));
    }

    private DetachedXadesSignatureBuilder buildDetachedXadesSignatureBuilder(List<HashcodeDataFile> dataFiles, SignatureParameters signatureParameters) {
        DigestAlgorithm digestAlgorithm = determineDigestAlgorithm();
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration)
                .withSigningCertificate(signatureParameters.getSigningCertificate())
                .withSignatureProfile(signatureParameters.getSignatureProfile())
                .withSignatureDigestAlgorithm(digestAlgorithm)
                .withCountry(signatureParameters.getCountry())
                .withStateOrProvince(signatureParameters.getStateOrProvince())
                .withCity(signatureParameters.getCity())
                .withPostalCode(signatureParameters.getPostalCode());

        for (HashcodeDataFile hashcodeDataFile : dataFiles) {
            builder = builder.withDataFile(convertDataFile(hashcodeDataFile));
        }
        if (signatureParameters.getRoles() != null && !signatureParameters.getRoles().isEmpty()) {
            String[] roles = new String[signatureParameters.getRoles().size()];
            builder = builder.withRoles(signatureParameters.getRoles().toArray(roles));
        }
        return builder;
    }

    private DigestAlgorithm determineDigestAlgorithm() {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (ServiceType.PROXY == sigaUserDetails.getServiceType()) {
            return DigestAlgorithm.SHA256;
        }
        return DigestAlgorithm.SHA512;
    }

    private HashcodeSignatureWrapper createSignatureWrapper(String signatureId, byte[] signature) {
        HashcodeSignatureWrapper signatureWrapper = new HashcodeSignatureWrapper();
        SignatureDataFilesParser parser = new SignatureDataFilesParser(signature);
        Map<String, String> dataFiles = parser.getEntries();
        signatureWrapper.setGeneratedSignatureId(signatureId);
        signatureWrapper.setSignature(signature);
        ContainerUtil.addSignatureDataFilesEntries(signatureWrapper, dataFiles);
        return signatureWrapper;
    }

    private DigestDataFile convertDataFile(HashcodeDataFile hashcodeDataFile) {
        String fileName = hashcodeDataFile.getFileName();
        DigestAlgorithm digestAlgorithm = determineDigestAlgorithm();

        String mimeType = hashcodeDataFile.getMimeType();
        return new DigestDataFile(fileName, digestAlgorithm, getDigest(hashcodeDataFile, digestAlgorithm), mimeType);
    }

    private byte[] getDigest(HashcodeDataFile hashcodeDataFile, DigestAlgorithm digestAlgorithm) {
        String fileHash;
        if (DigestAlgorithm.SHA256 == digestAlgorithm) {
            fileHash = hashcodeDataFile.getFileHashSha256();
        } else {
            fileHash = hashcodeDataFile.getFileHashSha512();
        }

        if (StringUtils.isBlank(fileHash)) {
            throw new TechnicalException("Unable to create signature. Unable to read file hash");
        }
        return Base64.getDecoder().decode(fileHash.getBytes());
    }

    private void verifyDataFileExistence(HashcodeContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataFiles().isEmpty()) {
            throw new InvalidSessionDataException("Unable to create signature. Data files must be added to container");
        }
    }

    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

}
