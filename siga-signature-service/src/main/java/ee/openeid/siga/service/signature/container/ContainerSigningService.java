package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.mobileid.GetStatusResponse;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MidStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
import ee.openeid.siga.service.signature.smartid.InitSmartIdSignatureResponse;
import ee.openeid.siga.service.signature.smartid.SigaSmartIdClient;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.SmartIdStatusResponse;
import ee.openeid.siga.session.SessionService;
import ee.sk.smartid.SmartIdCertificate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.DataToSign;
import org.digidoc4j.ServiceType;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.ValidationResult;
import org.digidoc4j.X509Cert;
import org.digidoc4j.exceptions.NetworkException;
import org.digidoc4j.exceptions.TechnicalException;
import org.digidoc4j.impl.ServiceAccessListener;
import org.digidoc4j.impl.ServiceAccessScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.function.Predicate;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.EXCEPTION;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.ErrorCode.SIGNATURE_FINALIZING_REQUEST_ERROR;
import static ee.openeid.siga.common.event.SigaEventName.EventParam.*;
import static ee.openeid.siga.common.event.SigaEventName.FINALIZE_SIGNATURE;

@Slf4j
public abstract class ContainerSigningService {

    private static final String UNABLE_TO_FINALIZE_SIGNATURE = "Unable to finalize signature";

    private SigaEventLogger sigaEventLogger;
    protected SessionService sessionService;
    private MobileIdClient mobileIdClient;
    private SigaSmartIdClient smartIdClient;

    public DataToSignWrapper createDataToSign(String containerId, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);

        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        DataToSignHolder dataToSignHolder = DataToSignHolder.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.REMOTE)
                .dataFilesHash(generateDataFilesHash(sessionHolder))
                .build();

        sessionHolder.addDataToSign(generatedSignatureId, dataToSignHolder);
        sessionService.update(containerId, sessionHolder);

        return DataToSignWrapper.builder()
                .dataToSign(dataToSign)
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public Result finalizeSigning(String containerId, String signatureId, String signatureValue) {
        Session sessionHolder = getSession(containerId);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        validateRemoteSession(dataToSignHolder, signatureId);

        byte[] base64Decoded = Base64.getDecoder().decode(signatureValue.getBytes());
        Signature signature = finalizeSignature(sessionHolder, containerId, signatureId, base64Decoded);

        addSignatureToSession(sessionHolder, signature, signatureId);
        sessionService.update(containerId, sessionHolder);
        return Result.OK;
    }

    public SigningChallenge startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);
        RelyingPartyInfo relyingPartyInfo = getRPInfoForMid();
        X509Certificate certificate = mobileIdClient.getCertificate(relyingPartyInfo, mobileIdInformation);
        signatureParameters.setSigningCertificate(certificate);
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        InitMidSignatureResponse initMidSignatureResponse = mobileIdClient.initMobileSigning(relyingPartyInfo, dataToSign, mobileIdInformation);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        DataToSignHolder dataToSignHolder = DataToSignHolder.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.MOBILE_ID)
                .sessionCode(initMidSignatureResponse.getSessionCode())
                .dataFilesHash(generateDataFilesHash(sessionHolder))
                .build();

        sessionHolder.addDataToSign(generatedSignatureId, dataToSignHolder);
        sessionService.update(containerId, sessionHolder);

        return SigningChallenge.builder()
                .challengeId(initMidSignatureResponse.getChallengeId())
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public String processMobileStatus(String containerId, String signatureId) {
        Session sessionHolder = getSession(containerId);
        validateMobileDeviceSession(sessionHolder.getDataToSignHolder(signatureId), signatureId, SigningType.MOBILE_ID);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        RelyingPartyInfo relyingPartyInfo = getRPInfoForMid();
        GetStatusResponse getStatusResponse = mobileIdClient.getStatus(dataToSignHolder.getSessionCode(), relyingPartyInfo);
        if (getStatusResponse.getStatus() == MidStatus.SIGNATURE) {
            Signature signature = finalizeSignature(sessionHolder, containerId, signatureId, getStatusResponse.getSignature());

            addSignatureToSession(sessionHolder, signature, signatureId);
            sessionService.update(containerId, sessionHolder);
        }
        return getStatusResponse.getStatus().name();
    }

    public String initSmartIdCertificateChoice(String containerId, SmartIdInformation smartIdInformation) {
        Session sessionHolder = getSession(containerId);
        RelyingPartyInfo relyingPartyInfo = getRPInfoForSmartId();
        String smartIdSessionId = smartIdClient.initiateCertificateChoice(relyingPartyInfo, smartIdInformation);
        String generatedCertificateId = UUIDGenerator.generateUUID();
        sessionHolder.addCertificateSessionId(generatedCertificateId, smartIdSessionId);
        sessionService.update(containerId, sessionHolder);
        return generatedCertificateId;
    }

    public CertificateStatus processSmartIdCertificateStatus(String containerId, String certificateId) {
        Session sessionHolder = getSession(containerId);
        String smartIdSessionId = sessionHolder.getCertificateSessionId(certificateId);
        if (smartIdSessionId == null) {
            throw new InvalidSessionDataException("No certificate session found with certificate Id: " + certificateId);
        }
        RelyingPartyInfo relyingPartyInfo = getRPInfoForSmartId();
        SmartIdStatusResponse smartIdStatusResponse = smartIdClient.getSmartIdCertificateStatus(relyingPartyInfo, smartIdSessionId);
        CertificateStatus certificateStatus = new CertificateStatus();
        if (smartIdStatusResponse.getStatus() == SmartIdSessionStatus.OK) {
            SmartIdCertificate smartIdCertificate = smartIdStatusResponse.getSmartIdCertificate();
            if (smartIdCertificate == null) {
                throw new IllegalArgumentException("No certificate found from Smart-id response");
            }
            sessionHolder.addCertificate(smartIdCertificate.getDocumentNumber(), smartIdCertificate.getCertificate());
            certificateStatus.setDocumentNumber(smartIdStatusResponse.getSmartIdCertificate().getDocumentNumber());
            sessionHolder.clearCertificateSessionId(certificateId);
            sessionService.update(containerId, sessionHolder);
        }
        certificateStatus.setStatus(smartIdStatusResponse.getStatus().getSigaCertificateMessage());
        return certificateStatus;
    }


    public SigningChallenge startSmartIdSigning(String containerId, SmartIdInformation smartIdInformation, SignatureParameters signatureParameters) {
        Session sessionHolder = getSession(containerId);
        verifySigningObjectExistence(sessionHolder);
        X509Certificate certificate = sessionHolder.getCertificate(smartIdInformation.getDocumentNumber());
        RelyingPartyInfo relyingPartyInfo = getRPInfoForSmartId();
        if (certificate == null) {
            SmartIdCertificate certificateResponse = smartIdClient.getCertificate(relyingPartyInfo, smartIdInformation);
            certificate = certificateResponse.getCertificate();
        }

        signatureParameters.setSigningCertificate(certificate);
        DataToSign dataToSign = buildDataToSign(sessionHolder, signatureParameters);

        InitSmartIdSignatureResponse initSmartIdSignatureResponse = smartIdClient.initSmartIdSigning(relyingPartyInfo, smartIdInformation, dataToSign);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        DataToSignHolder dataToSignHolder = DataToSignHolder.builder()
                .dataToSign(dataToSign)
                .signingType(SigningType.SMART_ID)
                .sessionCode(initSmartIdSignatureResponse.getSessionCode())
                .dataFilesHash(generateDataFilesHash(sessionHolder))
                .build();

        sessionHolder.addDataToSign(generatedSignatureId, dataToSignHolder);
        sessionHolder.clearCertificate(smartIdInformation.getDocumentNumber());
        sessionService.update(containerId, sessionHolder);

        return SigningChallenge.builder()
                .challengeId(initSmartIdSignatureResponse.getChallengeId())
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public String processSmartIdStatus(String containerId, String signatureId) {
        Session sessionHolder = getSession(containerId);
        validateMobileDeviceSession(sessionHolder.getDataToSignHolder(signatureId), signatureId, SigningType.SMART_ID);
        DataToSignHolder dataToSignHolder = sessionHolder.getDataToSignHolder(signatureId);
        RelyingPartyInfo relyingPartyInfo = getRPInfoForSmartId();
        SmartIdStatusResponse sessionResponse = smartIdClient.getSmartIdSigningStatus(relyingPartyInfo, dataToSignHolder.getSessionCode());
        if (sessionResponse.getStatus() == SmartIdSessionStatus.OK) {
            if (sessionResponse.getSignature() == null) {
                throw new IllegalArgumentException("No signature found from Smart-id response");
            }
            Signature signature = finalizeSignature(sessionHolder, containerId, signatureId, sessionResponse.getSignature());
            addSignatureToSession(sessionHolder, signature, signatureId);
            sessionService.update(containerId, sessionHolder);
        }

        return sessionResponse.getStatus().getSigaSigningMessage();
    }

    protected Signature finalizeSignature(Session session, String containerId, String signatureId, byte[] base64Decoded) {
        validateContainerDataFilesUnchanged(session, containerId, signatureId);
        DataToSign dataToSign = session.getDataToSignHolder(signatureId).getDataToSign();
        SigaEvent startEvent = sigaEventLogger.logStartEvent(FINALIZE_SIGNATURE).addEventParameter(SIGNATURE_ID, dataToSign.getSignatureParameters().getSignatureId());

        Signature signature;
        ServiceAccessListener listener = createServiceAccessListener();

        try (ServiceAccessScope ignored = new ServiceAccessScope(listener)) {
            signature = dataToSign.finalize(base64Decoded);
            validateFinalizedSignature(signature, startEvent);
            logEndEvent(startEvent, signature);
        } catch (TechnicalException e) {
            log.error(UNABLE_TO_FINALIZE_SIGNATURE, e);
            logExceptionEvent(startEvent, e);
            throw new SignatureCreationException(UNABLE_TO_FINALIZE_SIGNATURE);
        }
        return signature;
    }

    private void validateContainerDataFilesUnchanged(Session session, String containerId, String signatureId) {
        DataToSignHolder dataToSignHolder = session.getDataToSignHolder(signatureId);

        if (dataToSignHolder.getDataFilesHash() == null) {
            throw new IllegalStateException("Trying to finalize signature without container data files hash in session for data to sign");
        }

        if (!generateDataFilesHash(session).equals(dataToSignHolder.getDataFilesHash())) {
            session.clearSigning(signatureId);
            sessionService.update(containerId, session);
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". Container data files have been changed after signing was initiated. Repeat signing process");
        }
    }

    private ServiceAccessListener createServiceAccessListener() {
        return e -> {
            if (ServiceType.TSP == e.getServiceType()) {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.TSA_REQUEST, REQUEST_URL, e.getServiceUrl()));
            } else {
                sigaEventLogger.logEvent(SigaEvent.buildEventWithParameter(SigaEventName.OCSP_REQUEST, REQUEST_URL, e.getServiceUrl()));
            }
        };
    }

    private void validateFinalizedSignature(Signature signature, SigaEvent finalizationStartEvent) {
        ValidationResult validationResult = signature.validateSignature();
        if (!validationResult.isValid()) {
            IllegalStateException exception = new IllegalStateException("Signature validation failed");
            validationResult.getErrors().forEach(exception::addSuppressed);
            log.error(UNABLE_TO_FINALIZE_SIGNATURE, exception);
            sigaEventLogger.logExceptionEventFor(finalizationStartEvent, SIGNATURE_FINALIZING_ERROR, exception.getMessage());
            throw new SignatureCreationException(UNABLE_TO_FINALIZE_SIGNATURE);
        }
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

    private void logExceptionEvent(SigaEvent startEvent, TechnicalException e) {
        String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();

        if (e instanceof NetworkException) {
            NetworkException networkException = (NetworkException) e;
            String errorUrl = networkException.getServiceUrl();
            sigaEventLogger.logExceptionEventFor(startEvent, SIGNATURE_FINALIZING_REQUEST_ERROR, errorMessage);
            Predicate<SigaEvent> predicate = event -> event.containsParameterWithValue(errorUrl);
            sigaEventLogger.getFirstMachingEventAfter(startEvent, predicate).ifPresent(requestEventFromDigidoc -> {
                requestEventFromDigidoc.setErrorCode(SIGNATURE_FINALIZING_REQUEST_ERROR);
                requestEventFromDigidoc.setErrorMessage(errorMessage);
                requestEventFromDigidoc.setResultType(EXCEPTION);
            });
        } else {
            sigaEventLogger.logExceptionEventForIntermediateEvents(startEvent, SIGNATURE_FINALIZING_ERROR, errorMessage);
        }
    }

    private void validateRemoteSession(DataToSignHolder dataToSignHolder, String signatureId) {
        validateSession(dataToSignHolder, signatureId, SigningType.REMOTE);
    }

    private void validateMobileDeviceSession(DataToSignHolder dataToSignHolder, String signatureId, SigningType signingType) {
        validateSession(dataToSignHolder, signatureId, signingType);
        if (StringUtils.isBlank(dataToSignHolder.getSessionCode())) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". Session code not found");
        }
    }

    private void validateSession(DataToSignHolder dataToSignHolder, String signatureId, SigningType signingType) {
        if (dataToSignHolder == null || dataToSignHolder.getDataToSign() == null) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". No data to sign with signature Id: " + signatureId);
        }
        if (signingType != dataToSignHolder.getSigningType()) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + " for signing type: " + dataToSignHolder.getSigningType());
        }
    }

    private RelyingPartyInfo getRPInfoForMid() {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return RelyingPartyInfo.builder()
                .name(sigaUserDetails.getSkRelyingPartyName())
                .uuid(sigaUserDetails.getSkRelyingPartyUuid())
                .build();
    }

    private RelyingPartyInfo getRPInfoForSmartId() {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return RelyingPartyInfo.builder()
                .name(sigaUserDetails.getSmartIdRelyingPartyName())
                .uuid(sigaUserDetails.getSmartIdRelyingPartyUuid())
                .build();
    }

    public abstract DataToSign buildDataToSign(Session session, SignatureParameters signatureParameters);

    public abstract Session getSession(String containerId);

    public abstract void addSignatureToSession(Session sessionHolder, Signature signature, String signatureId);

    public SessionService getSessionService() {
        return sessionService;
    }

    public abstract void verifySigningObjectExistence(Session session);

    public abstract String generateDataFilesHash(Session session);

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Autowired
    public void setSigaEventLogger(SigaEventLogger sigaEventLogger) {
        this.sigaEventLogger = sigaEventLogger;
    }

    @Autowired
    public void setMobileIdClient(MobileIdClient mobileIdClient) {
        this.mobileIdClient = mobileIdClient;
    }

    @Autowired
    public void setSmartIdClient(SigaSmartIdClient smartIdClient) {
        this.smartIdClient = smartIdClient;
    }
}
