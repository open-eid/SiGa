package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.SigningType;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.DetachedDataFileContainerSessionHolder;
import ee.openeid.siga.mobileid.client.DigiDocService;
import ee.openeid.siga.mobileid.client.MobileIdService;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import ee.openeid.siga.service.signature.hashcode.SignatureDataFilesParser;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.DSSUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.*;
import org.digidoc4j.exceptions.TechnicalException;
import org.digidoc4j.impl.CommonOCSPSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static ee.openeid.siga.common.event.SigaEvent.EventType.FINISH;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.OCSP_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.TSA_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.*;
import static ee.openeid.siga.common.event.SigaEventName.OCSP;
import static ee.openeid.siga.common.event.SigaEventName.TSA_REQUEST;
import static java.time.Instant.ofEpochMilli;

@Slf4j
@Service
public class DetachedDataFileContainerSigningService implements DetachedDataFileSessionHolder {

    private static final String OK_RESPONSE = "OK";
    @Autowired
    private DigiDocService digiDocService;
    @Autowired
    private MobileIdService mobileIdService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private SigaEventLogger sigaEventLogger;

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
        X509Certificate signingCertificate = digiDocService.getMobileX509Certificate(mobileIdInformation.getPersonIdentifier(), mobileIdInformation.getCountry(), mobileIdInformation.getPhoneNo());
        signatureParameters.setSigningCertificate(signingCertificate);
        DataToSign dataToSign = buildDetachedXadesSignatureBuilder(sessionHolder.getDataFiles(), signatureParameters).buildDataToSign();
        byte[] digest = DSSUtils.digest(dataToSign.getDigestAlgorithm().getDssDigestAlgorithm(), dataToSign.getDataToSign());
        MobileSignHashResponse response = mobileIdService.initMobileSignHash(mobileIdInformation, dataToSign.getDigestAlgorithm().name(), Hex.encodeHexString(digest));
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
        GetMobileSignHashStatusResponse getMobileSignHashStatusResponse = mobileIdService.getMobileSignHashStatus(sessionHolder.getSessionCode());
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
        CommonOCSPSource commonOCSPSource = new CommonOCSPSource(configuration);
        final String ocspUrl = commonOCSPSource.getAccessLocation(dataToSign.getSignatureParameters().getSigningCertificate());
        final String tsaUrl = configuration.getTspSource();
        SigaEvent ocspStartEvent = logSignatureStartEvent(ocspUrl, tsaUrl);
        try {
            Signature signature = dataToSign.finalize(base64Decoded);
            logSignatureEvents(ocspStartEvent, ocspUrl, tsaUrl, signature);
            return signature;
        } catch (TechnicalException e) {
            log.error("Unable to finalize signature", e);
            logSignatureExceptionEvent(ocspStartEvent, ocspUrl, tsaUrl, e);
            throw new ee.openeid.siga.common.exception.TechnicalException("Unable to finalize signature");
        }
    }

    @NotNull
    private SigaEvent logSignatureStartEvent(String ocspUrl, String tsaUrl) {
        SigaEvent ocspStartEvent = sigaEventLogger.logStartEvent(OCSP);
        ocspStartEvent.addEventParameter(OCSP_URL, ocspUrl);
        ocspStartEvent.addEventParameter(TSA_URL, tsaUrl);
        return ocspStartEvent;
    }

    private void logSignatureEvents(SigaEvent ocspStartEvent, String ocspUrl, String tsaUrl, Signature signature) {
        X509Cert timestampCert = signature.getTimeStampTokenCertificate();
        if (timestampCert != null) {
            SigaEvent tsaRequestEvent = sigaEventLogger.logEndEvent(TSA_REQUEST);
            tsaRequestEvent.addEventParameter(TSA_SUBJECT_CA, signature.getTimeStampTokenCertificate().getSubjectName());
            tsaRequestEvent.addEventParameter(TSA_URL, tsaUrl);
        }
        SigaEvent ocspEndEvent = sigaEventLogger.logEndEvent(OCSP);
        ocspEndEvent.addEventParameter(OCSP_SUBJECT_CA, signature.getOCSPCertificate().getSubjectName());
        ocspEndEvent.addEventParameter(OCSP_URL, ocspUrl);
        long executionTimeInMilli = Duration.between(ofEpochMilli(ocspStartEvent.getTimestamp()), ofEpochMilli(ocspEndEvent.getTimestamp())).toMillis();
        ocspEndEvent.setDuration(executionTimeInMilli);
        sigaEventLogger.getFirstMachingEvent(TSA_REQUEST, FINISH).ifPresent(e -> e.setDuration(executionTimeInMilli));
    }

    private void logSignatureExceptionEvent(SigaEvent ocspStartEvent, String ocspUrl, String tsaUrl, TechnicalException e) {
        SigaEvent signatureExceptionEvent = sigaEventLogger.logExceptionEvent(OCSP);
        signatureExceptionEvent.addEventParameter(OCSP_URL, ocspUrl);
        signatureExceptionEvent.addEventParameter(TSA_URL, tsaUrl);
        signatureExceptionEvent.setErrorCode(OCSP_REQUEST_ERROR);
        signatureExceptionEvent.setErrorMessage(e.getMessage());
        if (e.getCause() != null && StringUtils.isNotBlank(e.getCause().getMessage())) {
            String errorUrl = StringUtils.substringBetween(e.getCause().getMessage(), "'", "'");
            if (configuration.getTspSource().equals(errorUrl)) {
                signatureExceptionEvent.setEventName(TSA_REQUEST);
                signatureExceptionEvent.setErrorCode(TSA_REQUEST_ERROR);
                signatureExceptionEvent.setErrorMessage(errorUrl);
            } else if (ocspUrl.equals(errorUrl)) {
                signatureExceptionEvent.setErrorCode(OCSP_REQUEST_ERROR);
                signatureExceptionEvent.setErrorMessage(errorUrl);
            }
        }
        long executionTimeInMilli = Duration.between(ofEpochMilli(ocspStartEvent.getTimestamp()), ofEpochMilli(signatureExceptionEvent.getTimestamp())).toMillis();
        signatureExceptionEvent.setDuration(executionTimeInMilli);
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
}
