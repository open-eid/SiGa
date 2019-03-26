package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.SigningType;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.mobileid.client.DdsService;
import ee.openeid.siga.mobileid.client.MidService;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import ee.openeid.siga.service.signature.hashcode.SignatureDataFilesParser;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.*;
import org.digidoc4j.exceptions.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class DetachedDataFileContainerSigningService implements DetachedDataFileSessionHolder {

    private static final String OK_RESPONSE = "OK";
    private DdsService ddsService;
    private MidService midService;
    private SessionService sessionService;
    private Configuration configuration;

    public DataToSign createDataToSign(String containerId, SignatureParameters signatureParameters) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        verifyDataFileExistence(sessionHolder);
        DataToSign dataToSign = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
        sessionHolder.setDataToSign(dataToSign);
        sessionHolder.setSigningType(SigningType.REMOTE);
        sessionService.update(containerId, sessionHolder);
        return dataToSign;
    }

    public String finalizeSigning(String containerId, String signatureValue) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        validateRemoteSession(sessionHolder);
        DataToSign dataToSign = sessionHolder.getDataToSign();

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = finalizeSignature(dataToSign, base64Decoded);
        SignatureWrapper signatureWrapper = createSignatureWrapper(signature.getAdESSignature());

        sessionHolder.getSignatures().add(signatureWrapper);
        sessionHolder.clearSigning();
        sessionService.update(containerId, sessionHolder);
        return SessionResult.OK.name();
    }

    public String startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        verifyDataFileExistence(sessionHolder);
        X509Certificate signingCertificate = ddsService.getMobileCertificate(mobileIdInformation.getPersonIdentifier(), mobileIdInformation.getCountry(), mobileIdInformation.getPhoneNo());
        signatureParameters.setSigningCertificate(signingCertificate);
        DataToSign dataToSign = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
        byte[] digest = DSSUtils.digest(dataToSign.getDigestAlgorithm().getDssDigestAlgorithm(), dataToSign.getDataToSign());
        MobileSignHashResponse response = midService.initMobileSignHash(mobileIdInformation, dataToSign.getDigestAlgorithm().name(), Hex.encodeHexString(digest));
        if (!OK_RESPONSE.equals(response.getStatus())) {
            throw new IllegalStateException("Invalid DigiDocService response");
        }
        sessionHolder.setDataToSign(dataToSign);
        sessionHolder.setSigningType(SigningType.MOBILE_ID);
        sessionHolder.setSessionCode(response.getSesscode());
        sessionService.update(containerId, sessionHolder);
        return response.getChallengeID();
    }

    public String processMobileStatus(String containerId) {
        DetachedDataFileContainerSessionHolder sessionHolder = getSession(containerId);
        validateMobileIdSession(sessionHolder);
        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = midService.getMobileSignHashStatus(sessionHolder.getSessionCode());
        ProcessStatusType status = getMobileSignHashStatusResponse.getStatus();
        if (ProcessStatusType.SIGNATURE == status) {
            DataToSign dataToSign = sessionHolder.getDataToSign();
            Signature signature = finalizeSignature(dataToSign, getMobileSignHashStatusResponse.getSignature());
            SignatureWrapper signatureWrapper = createSignatureWrapper(signature.getAdESSignature());
            sessionHolder.getSignatures().add(signatureWrapper);
            sessionHolder.clearSigning();
            sessionService.update(containerId, sessionHolder);
        }
        return status.name();
    }

    private DetachedXadesSignatureBuilder buildDetachedXadesSignatureBuilder(List<HashcodeDataFile> dataFiles, SignatureParameters signatureParameters) {
        DetachedXadesSignatureBuilder builder = DetachedXadesSignatureBuilder.withConfiguration(configuration)
                .withSigningCertificate(signatureParameters.getSigningCertificate())
                .withSignatureProfile(signatureParameters.getSignatureProfile())
                .withSignatureDigestAlgorithm(DigestAlgorithm.SHA512)
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

    private SignatureWrapper createSignatureWrapper(byte[] signature) {
        SignatureWrapper signatureWrapper = new SignatureWrapper();
        SignatureDataFilesParser parser = new SignatureDataFilesParser(signature);
        Map<String, String> dataFiles = parser.getEntries();
        signatureWrapper.setGeneratedSignatureId(SessionIdGenerator.generateSessionId());
        signatureWrapper.setSignature(signature);
        ContainerUtil.addSignatureDataFilesEntries(signatureWrapper, dataFiles);
        return signatureWrapper;
    }

    private Signature finalizeSignature(DataToSign dataToSign, byte[] base64Decoded) {
//        CommonOCSPSource commonOCSPSource = new CommonOCSPSource(configuration);
//        String ocspUrl = commonOCSPSource.getAccessLocation(dataToSign.getSignatureParameters().getSigningCertificate());
        //TODO: digidoc4j 3.1.0 needed
        try {

            Signature signature = dataToSign.finalize(base64Decoded);
//                    TSP status: success {url:configuration.getTspSource()}
//                    OCSP status: success {url:ocspUrl}
            if (signature.getOCSPCertificate() == null) {
//                    TSP status: success
//                    OCSP status: failure
            }
            return signature;
        } catch (TechnicalException e) {
            if (e.getCause() != null && StringUtils.isNotBlank(e.getCause().getMessage())) {
                String errorUrl = StringUtils.substringBetween(e.getCause().getMessage(), "'", "'");
                if (configuration.getTspSource().equals(errorUrl)) {
//                    TSP status: failure
                }
                //TODO: digidoc4j 3.1.0 needed
            }
            throw new ee.openeid.siga.common.exception.TechnicalException("Unable to finalize signature");
        }
    }

    private DigestDataFile convertDataFile(HashcodeDataFile hashcodeDataFile) {
        String fileName = hashcodeDataFile.getFileName();
        DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA512;
        if (StringUtils.isBlank(hashcodeDataFile.getFileHashSha512())) {
            throw new TechnicalException("Unable to create signature. Unable to read file hash");
        }
        byte[] digest = Base64.getDecoder().decode(hashcodeDataFile.getFileHashSha512().getBytes());
        return new DigestDataFile(fileName, digestAlgorithm, digest);
    }

    private void verifyDataFileExistence(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataFiles().size() < 1) {
            throw new InvalidSessionDataException("Unable to create signature. Data files must be added to container");
        }
    }

    private void validateRemoteSession(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataToSign() == null) {
            throw new InvalidSessionDataException("Unable to finalize signature. Invalid session found");
        }
        if (SigningType.REMOTE != sessionHolder.getSigningType()) {
            throw new InvalidSessionDataException("Unable to finalize signature");
        }
    }

    private void validateMobileIdSession(DetachedDataFileContainerSessionHolder sessionHolder) {
        if (sessionHolder.getDataToSign() == null) {
            throw new InvalidSessionDataException("Unable to finalize signature. Invalid session found");
        }
        if (StringUtils.isBlank(sessionHolder.getSessionCode())) {
            throw new InvalidSessionDataException("Unable to finalize signature. Session code not found");
        }
        if (SigningType.MOBILE_ID != sessionHolder.getSigningType()) {
            throw new InvalidSessionDataException("Unable to finalize signature");
        }
    }

    @Override
    public SessionService getSessionService() {
        return sessionService;
    }

    @Autowired
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    protected void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setDdsService(DdsService ddsService) {
        this.ddsService = ddsService;
    }

    @Autowired
    public void setMidService(MidService midService) {
        this.midService = midService;
    }

}
