package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.exception.DataFileNotFoundException;
import ee.openeid.siga.common.exception.DataToSignNotFoundException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.mobileid.client.MobileService;
import ee.openeid.siga.mobileid.model.MobileSignHashResponse;
import ee.openeid.siga.service.signature.hashcode.SignatureDataFilesParser;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSUtils;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DetachedDataFileContainerSigningService implements DetachedDataFileSessionHolder {

    private static final String OK_RESPONSE = "OK";
    private MobileService mobileService;
    private SessionService sessionService;
    private Configuration configuration = new Configuration();

    public DataToSign createDataToSign(String containerId, SignatureParameters signatureParameters) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        verifyDataFileExistence(sessionHolder);
        DataToSign dataToSign = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
        sessionHolder.setDataToSign(dataToSign);
        sessionService.update(containerId, sessionHolder);
        return dataToSign;
    }

    public String finalizeSigning(String containerId, String signatureValue) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        verifyDataToSignExistence(sessionHolder);
        DataToSign dataToSign = sessionHolder.getDataToSign();

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = dataToSign.finalize(base64Decoded);
        SignatureWrapper signatureWrapper = createSignatureWrapper(signature.getAdESSignature());

        sessionHolder.getSignatures().add(signatureWrapper);
        sessionHolder.setDataToSign(null);
        sessionService.update(containerId, sessionHolder);
        return SessionResult.OK.name();
    }

    public String startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        verifyDataFileExistence(sessionHolder);
        X509Certificate signingCertificate = mobileService.getMobileCertificate(mobileIdInformation.getPersonIdentifier(), mobileIdInformation.getCountry());
        signatureParameters.setSigningCertificate(signingCertificate);
        DataToSign dataToSign = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
        byte[] digest = DSSUtils.digest(dataToSign.getDigestAlgorithm().getDssDigestAlgorithm(), dataToSign.getDataToSign());
        MobileSignHashResponse response = mobileService.initMobileSignHash(mobileIdInformation, dataToSign.getDigestAlgorithm().name(), Hex.encodeHexString(digest));
        if (!OK_RESPONSE.equals(response.getStatus())) {
            throw new IllegalStateException("Invalid DigiDocService response");
        }
        sessionHolder.setSessionCode(response.getSesscode());
        sessionService.update(containerId, sessionHolder);
        return response.getChallengeID();
    }

    private DetachedXadesSignatureBuilder buildDetachedXadesSignatureBuilder(List<HashCodeDataFile> dataFiles, SignatureParameters signatureParameters) {
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration)
                .withSigningCertificate(signatureParameters.getSigningCertificate())
                .withSignatureProfile(signatureParameters.getSignatureProfile())
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
                .withCountry(signatureParameters.getCountry())
                .withStateOrProvince(signatureParameters.getStateOrProvince())
                .withCity(signatureParameters.getCity())
                .withPostalCode(signatureParameters.getPostalCode());

        for (HashCodeDataFile hashCodeDataFile : dataFiles) {
            builder = builder.withDataFile(convertDataFile(hashCodeDataFile));
        }
        if (signatureParameters.getRoles() != null && !signatureParameters.getRoles().isEmpty()) {
            String[] roles = new String[signatureParameters.getRoles().size()];
            builder = builder.withRoles(signatureParameters.getRoles().toArray(roles));
        }
        return builder;
    }

    private SignatureWrapper createSignatureWrapper(byte[] signature) {
        SignatureWrapper signatureWrapper = new SignatureWrapper();
        SignatureDataFilesParser parser = new SignatureDataFilesParser(signature);
        Map<String, String> dataFiles = parser.getEntries();
        signatureWrapper.setSignature(signature);
        ContainerUtil.addSignatureDataFilesEntries(signatureWrapper, dataFiles);
        return signatureWrapper;
    }

    private DigestDataFile convertDataFile(HashCodeDataFile hashCodeDataFile) {
        String fileName = hashCodeDataFile.getFileName();
        DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA512;
        byte[] digest = hashCodeDataFile.getFileHashSha512().getBytes();
        return new DigestDataFile(fileName, digestAlgorithm, digest);
    }

    private void verifyDataFileExistence(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataFiles().size() < 1) {
            throw new DataFileNotFoundException("Unable to create signature. Data files must be added to container");
        }
    }

    private void verifyDataToSignExistence(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataToSign() == null) {
            throw new DataToSignNotFoundException("Unable to finalize signature. Invalid session found");
        }
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    protected void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setMobileService(MobileService mobileService) {
        this.mobileService = mobileService;
    }
}
