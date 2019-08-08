package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.DataToSignWrapper;
import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.common.Result;
import ee.openeid.siga.common.SigningChallenge;
import ee.openeid.siga.common.SigningType;
import ee.openeid.siga.common.SmartIdInformation;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import ee.openeid.siga.service.signature.configuration.SmartIdServiceConfigurationProperties;
import ee.openeid.siga.service.signature.mobileid.GetStatusResponse;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
import ee.openeid.siga.session.SessionService;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionStatus;
import eu.europa.esig.dss.DSSUtils;
import eu.europa.esig.dss.DigestAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.DataToSign;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.X509Cert;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.function.Predicate;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.ISSUING_CA;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.SIGNATURE_ID;
import static ee.openeid.siga.common.event.SigaEventName.FINALIZE_SIGNATURE;

@Slf4j
public abstract class ContainerSigningService {
    private static final String SMART_ID_CERTIFICATE_LEVEL = "QUALIFIED";
    private static final String SMART_ID_FINISHED_STATE = "COMPLETE";
    private SigaEventLogger sigaEventLogger;
    protected SessionService sessionService;
    private MobileIdClient mobileIdClient;
    private SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties;

    public DataToSignWrapper createDataToSign(String containerId, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);
        String generatedSignatureId = UUIDGenerator.generateUUID();
        sessionHolder.addDataToSign(generatedSignatureId, DataToSignHolder.builder().dataToSign(dataToSign).signingType(SigningType.REMOTE).build());
        sessionService.update(containerId, sessionHolder);
        return DataToSignWrapper.builder().dataToSign(dataToSign).generatedSignatureId(generatedSignatureId).build();
    }

    public Result finalizeSigning(String containerId, String signatureId, String signatureValue) {
        Session sessionHolder = getSession(containerId);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        validateRemoteSession(dataToSignHolder, signatureId);
        DataToSign dataToSign = dataToSignHolder.getDataToSign();

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = finalizeSignature(dataToSign, base64Decoded);

        addSignatureToSession(sessionHolder, signature, signatureId);
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public SigningChallenge startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);

        X509Certificate certificate = mobileIdClient.getCertificate(mobileIdInformation);
        signatureParameters.setSigningCertificate(certificate);
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        InitMidSignatureResponse initMidSignatureResponse = mobileIdClient.initMobileSigning(dataToSign, mobileIdInformation);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        sessionHolder.addDataToSign(generatedSignatureId, DataToSignHolder.builder().dataToSign(dataToSign).signingType(SigningType.MOBILE_ID).sessionCode(initMidSignatureResponse.getSessionCode()).build());
        sessionService.update(containerId, sessionHolder);

        return SigningChallenge.builder().challengeId(initMidSignatureResponse.getChallengeId()).generatedSignatureId(generatedSignatureId).build();
    }

    public String processMobileStatus(String containerId, String signatureId) {
        Session sessionHolder = getSession(containerId);
        validateMobileDeviceSession(sessionHolder.getDataToSignHolder(signatureId), signatureId, SigningType.MOBILE_ID);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        GetStatusResponse getStatusResponse = mobileIdClient.getStatus(dataToSignHolder.getSessionCode());
        String status = getStatusResponse.getStatus();
        if (ProcessStatusType.SIGNATURE.name().equals(status) || MobileIdClient.OK_RESPONSE.equals(status)) {
            DataToSign dataToSign = dataToSignHolder.getDataToSign();
            Signature signature = finalizeSignature(dataToSign, getStatusResponse.getSignature());

            addSignatureToSession(sessionHolder, signature, signatureId);
            sessionService.update(containerId, sessionHolder);
        }
        return status;
    }

    public SigningChallenge startSmartIdSigning(String containerId, SmartIdInformation smartIdInformation, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);
        NationalIdentity nationalIdentity = new NationalIdentity(smartIdInformation.getCountry(), smartIdInformation.getPersonIdentifier());
        SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
        SmartIdCertificate certificateResponse = smartIdClient
                .getCertificate()
                .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                .withNationalIdentity(nationalIdentity)
                .fetch();

        signatureParameters.setSigningCertificate(certificateResponse.getCertificate());

        String documentNumber = certificateResponse.getDocumentNumber();
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        SignableHash signableHash = createSignableHash(dataToSign);
        String challengeId = signableHash.calculateVerificationCode();

        String sessionId = initSmartIdSign(smartIdClient, smartIdInformation, signableHash, documentNumber);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        sessionHolder.addDataToSign(generatedSignatureId, DataToSignHolder.builder().dataToSign(dataToSign).signingType(SigningType.SMART_ID).sessionCode(sessionId).build());
        sessionService.update(containerId, sessionHolder);

        return SigningChallenge.builder().challengeId(challengeId).generatedSignatureId(generatedSignatureId).build();

    }

    public String processSmartIdStatus(String containerId, String signatureId, SmartIdInformation smartIdInformation) {
        Session sessionHolder = getSession(containerId);
        validateMobileDeviceSession(sessionHolder.getDataToSignHolder(signatureId), signatureId, SigningType.SMART_ID);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        SmartIdClient smartIdClient = createSmartIdClient(smartIdInformation);
        SessionStatus sessionStatus = smartIdClient.getSmartIdConnector().getSessionStatus(dataToSignHolder.getSessionCode());
        String signatureValue = sessionStatus.getSignature().getValue();
        if (SMART_ID_FINISHED_STATE.equals(sessionStatus.getState())) {
            DataToSign dataToSign = dataToSignHolder.getDataToSign();
            Signature signature = finalizeSignature(dataToSign, Base64.getDecoder().decode(signatureValue.getBytes()));

            addSignatureToSession(sessionHolder, signature, signatureId);
            sessionService.update(containerId, sessionHolder);
        }
        return sessionStatus.getState();
    }


    /**
     * TSP/OCSP request events are currently generated by intercepting logging events from
     * digidoc4j library. Jira task DD4J-415 will introduce new features to observe
     * requests made to TSP/OCSP providers.
     *
     * @see <a href="https://jira.ria.ee/browse/DD4J-415">Jira task DD4J-415</a>
     */
    protected Signature finalizeSignature(DataToSign dataToSign, byte[] base64Decoded) {
        SigaEvent startEvent = sigaEventLogger.logStartEvent(FINALIZE_SIGNATURE).addEventParameter(SIGNATURE_ID, dataToSign.getSignatureParameters().getSignatureId());
        try {
            Signature signature = dataToSign.finalize(base64Decoded);
            logEndEvent(startEvent, signature);
            return signature;
        } catch (TechnicalException e) {
            log.error("Unable to finalize signature", e);
            logExceptionEvent(startEvent, e);
            throw new SignatureCreationException("Unable to finalize signature");
        }
    }


    private SignableHash createSignableHash(DataToSign dataToSign) {
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(digest);
        signableHash.setHashType(HashType.SHA512);
        return signableHash;
    }

    private String initSmartIdSign(SmartIdClient smartIdClient, SmartIdInformation smartIdInformation, SignableHash signableHash, String documentNumber) {
        return smartIdClient
                .createSignature()
                .withDocumentNumber(documentNumber)
                .withSignableHash(signableHash)
                .withDisplayText(smartIdInformation.getMessageToDisplay())
                .withCertificateLevel(SMART_ID_CERTIFICATE_LEVEL)
                .initiateSigning();
    }

    private SmartIdClient createSmartIdClient(SmartIdInformation smartIdInformation) {
        SmartIdClient client = new SmartIdClient();
        client.setHostUrl(smartIdServiceConfigurationProperties.getUrl());
        client.setRelyingPartyName(smartIdInformation.getRelyingPartyName());
        client.setRelyingPartyUUID(smartIdInformation.getRelyingPartyUuid());
        return client;
    }

    private void logEndEvent(SigaEvent startEvent, Signature signature) {
        X509Cert tstCert = signature.getTimeStampTokenCertificate();
        if (tstCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.TSA_REQUEST.equals(e.getEventName())).ifPresent(e ->
                    e.addEventParameter(ISSUING_CA, tstCert.issuerName())
            );
        }
        X509Cert ocspCert = signature.getOCSPCertificate();
        if (ocspCert != null) {
            sigaEventLogger.getLastMachingEvent(e -> SigaEventName.OCSP_REQUEST.equals(e.getEventName())).ifPresent(e ->
                    e.addEventParameter(ISSUING_CA, ocspCert.issuerName())
            );
        }
        SigaEvent endEvent = sigaEventLogger.logEndEventFor(startEvent);
        endEvent.addEventParameter(SIGNATURE_ID, signature.getId());
    }

    private void logExceptionEvent(SigaEvent ocspStartEvent, TechnicalException e) {
        String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

        if (e instanceof NetworkException) {
            NetworkException networkException = (NetworkException) e;
            String errorUrl = networkException.getServiceUrl();
            sigaEventLogger.logExceptionEventFor(ocspStartEvent, SIGNATURE_FINALIZING_REQUEST_ERROR, errorMessage);
            Predicate<SigaEvent> predicate = event -> event.containsParameterWithValue(errorUrl);
            sigaEventLogger.getFirstMachingEventAfter(ocspStartEvent, predicate).ifPresent(requestEventFromDigidoc -> {
                requestEventFromDigidoc.setErrorCode(SIGNATURE_FINALIZING_REQUEST_ERROR);
                requestEventFromDigidoc.setErrorMessage(errorMessage);
                requestEventFromDigidoc.setResultType(EXCEPTION);
            });
        } else {
            sigaEventLogger.logExceptionEventForIntermediateEvents(ocspStartEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
        }
    }

    private void validateRemoteSession(DataToSignHolder dataToSignHolder, String signatureId) {
        validateSession(dataToSignHolder, signatureId, SigningType.REMOTE);
    }

    private void validateMobileDeviceSession(DataToSignHolder dataToSignHolder, String signatureId, SigningType signingType) {
        validateSession(dataToSignHolder, signatureId, signingType);
        if (StringUtils.isBlank(dataToSignHolder.getSessionCode())) {
            throw new InvalidSessionDataException("Unable to finalize signature. Session code not found");
        }
    }

    private void validateSession(DataToSignHolder dataToSignHolder, String signatureId, SigningType signingType) {
        if (dataToSignHolder == null || dataToSignHolder.getDataToSign() == null) {
            throw new InvalidSessionDataException("Unable to finalize signature. No data to sign with signature Id: " + signatureId);
        }
        if (signingType != dataToSignHolder.getSigningType()) {
            throw new InvalidSessionDataException("Unable to finalize signature for signing type: " + dataToSignHolder.getSigningType());
        }
    }

    public abstract DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters);

    public abstract Session getSession(String containerId);

    public abstract void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId);

    public SessionService getSessionService() {
        return sessionService;
    }

    public abstract void verifySigningObjectExistence(Session session);

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setSigaEventLogger(SigaEventLogger sigaEventLogger) {
        this.sigaEventLogger = sigaEventLogger;
    }

    @Autowired
    void setSmartIdServiceConfigurationProperties(SmartIdServiceConfigurationProperties smartIdServiceConfigurationProperties) {
        this.smartIdServiceConfigurationProperties = smartIdServiceConfigurationProperties;
    }

    @Autowired
    public void setMobileIdClient(MobileIdClient mobileIdClient) {
        this.mobileIdClient = mobileIdClient;
    }
}
