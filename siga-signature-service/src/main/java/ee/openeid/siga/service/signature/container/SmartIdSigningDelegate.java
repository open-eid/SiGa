package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SessionStatus.StatusError;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.smartid.InitSmartIdSignatureResponse;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.SmartIdStatusResponse;
import ee.sk.smartid.SmartIdCertificate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.ignite.IgniteSemaphore;
import org.digidoc4j.DataToSign;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INTERNAL_SERVER_ERROR;
import static ee.openeid.siga.common.exception.ErrorResponseCode.SMARTID_EXCEPTION;
import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;
import static ee.openeid.siga.service.signature.container.ContainerSigningService.UNABLE_TO_FINALIZE_SIGNATURE;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
@RequiredArgsConstructor
public class SmartIdSigningDelegate {
    private final ContainerSigningService containerSigningService;

    public static RelyingPartyInfo getRelyingPartyInfo() {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return RelyingPartyInfo.builder()
                .name(sigaUserDetails.getSmartIdRelyingPartyName())
                .uuid(sigaUserDetails.getSmartIdRelyingPartyUuid())
                .build();
    }

    public SigningChallenge startSmartIdSigning(String containerId, SmartIdInformation smartIdInformation, SignatureParameters signatureParameters) {
        Session session = containerSigningService.getSession(containerId);
        containerSigningService.verifySigningObjectExistence(session);
        X509Certificate certificate = session.getCertificate(smartIdInformation.getDocumentNumber());
        RelyingPartyInfo relyingPartyInfo = getRelyingPartyInfo();
        if (certificate == null) {
            SmartIdCertificate certificateResponse = containerSigningService.getSmartIdApiClient().getCertificate(relyingPartyInfo, smartIdInformation);
            certificate = certificateResponse.getCertificate();
        }

        signatureParameters.setSigningCertificate(certificate);
        DataToSign dataToSign = containerSigningService.buildDataToSign(session, signatureParameters);

        InitSmartIdSignatureResponse initSmartIdSignatureResponse = containerSigningService.getSmartIdApiClient().initSmartIdSigning(relyingPartyInfo, smartIdInformation, dataToSign);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        SignatureSession signatureSession = SignatureSession.builder()
                .relyingPartyInfo(relyingPartyInfo)
                .dataToSign(dataToSign)
                .signingType(SMART_ID)
                .sessionCode(initSmartIdSignatureResponse.getSessionCode())
                .dataFilesHash(containerSigningService.generateDataFilesHash(session))
                .build();

        session.addSignatureSession(generatedSignatureId, signatureSession);
        session.clearCertificate(smartIdInformation.getDocumentNumber());
        pollSmartIdSignatureStatus(session.getSessionId(), generatedSignatureId,
                containerSigningService.getSmartIdConfigurationProperties().getStatusPollingDelay());
        containerSigningService.getSessionService().update(session);

        return SigningChallenge.builder()
                .challengeId(initSmartIdSignatureResponse.getChallengeId())
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public String initSmartIdCertificateChoice(String containerId, SmartIdInformation smartIdInformation) {
        Session session = containerSigningService.getSession(containerId);
        containerSigningService.verifySigningObjectExistence(session);
        RelyingPartyInfo relyingPartyInfo = getRelyingPartyInfo();
        String smartIdSessionId = containerSigningService.getSmartIdApiClient().initiateCertificateChoice(relyingPartyInfo, smartIdInformation);
        String generatedCertificateId = UUIDGenerator.generateUUID();
        session.addCertificateSession(generatedCertificateId, CertificateSession.builder()
                .relyingPartyInfo(relyingPartyInfo)
                .sessionCode(smartIdSessionId)
                .build());
        pollSmartIdCertificateStatus(session.getSessionId(), generatedCertificateId,
                containerSigningService.getSmartIdConfigurationProperties().getStatusPollingDelay());
        containerSigningService.getSessionService().update(session);
        return generatedCertificateId;
    }

    public CertificateStatus getSmartIdCertificateStatus(String containerId, String certificateId) {
        Session session = containerSigningService.getSession(containerId);
        if (session == null || session.getCertificateSession(certificateId) == null) {
            throw new InvalidSessionDataException("No session found for certificate Id: " + certificateId);
        }
        CertificateSession certificateSession = session.getCertificateSession(certificateId);
        SessionStatus sessionStatus = certificateSession.getSessionStatus();
        String status = sessionStatus.getStatus();
        StatusError statusError = sessionStatus.getStatusError();
        if (sessionStatus.getProcessingStatus() == RESULT) {
            session.removeCertificateSession(certificateId);
            containerSigningService.getSessionService().update(session);
            return CertificateStatus.builder()
                    .status(status)
                    .documentNumber(certificateSession.getDocumentNumber())
                    .build();
        } else {
            int maxProcessingAttempts = containerSigningService.getReprocessingProperties().getMaxProcessingAttempts();
            if (sessionStatus.getProcessingCounter() >= maxProcessingAttempts) {
                ErrorResponseCode errorResponseCode = EnumUtils.getEnum(ErrorResponseCode.class, statusError.getErrorCode(), INTERNAL_SERVER_ERROR);
                throw new SigaApiException(errorResponseCode, statusError.getErrorMessage());
            } else {
                return CertificateStatus.builder()
                        .status(SmartIdSessionStatus.RUNNING.getSigaCertificateMessage())
                        .documentNumber(certificateSession.getDocumentNumber())
                        .build();
            }
        }
    }

    public void pollSmartIdCertificateStatus(String sessionId, String certificateId, Duration pollingDelay) {
        Runnable pollingRunnable = () -> {
            // If semaphore is not acquired it will be re-processed by SessionStatusReprocessingService
            IgniteSemaphore semaphore = containerSigningService.getIgnite().semaphore(certificateId, 1, true, true);
            if (semaphore.tryAcquire()) {
                try (semaphore) {
                    pollCertificateStatus(sessionId, certificateId);
                } catch (Exception ex) {
                    setPollingException(sessionId, certificateId, ex);
                } finally {
                    // Semaphore release conditions 1) Normal execution 2) Exception occurs 3) Ignite node leaves topology
                    containerSigningService.getSigaEventLogger().logEvents();
                    log.debug("Status polling unlocked for certificate id: {}", certificateId);
                }
            } else {
                log.debug("Status polling semaphore not acquired for certificate id: {}", certificateId);
            }
        };
        DelegatingSecurityContextRunnable delegatingRunnable = new DelegatingSecurityContextRunnable(pollingRunnable);
        CompletableFuture.runAsync(delegatingRunnable, delayedExecutor(pollingDelay.toMillis(), MILLISECONDS, containerSigningService.getTaskExecutor()));
    }

    private void pollCertificateStatus(String sessionId, String certificateId) {
        log.debug("Status polling locked for certificate id: {}", certificateId);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        CertificateSession certificateSession = session.getCertificateSession(certificateId);
        RelyingPartyInfo relyingPartyInfo = certificateSession.getRelyingPartyInfo();
        SmartIdStatusResponse statusResponse = containerSigningService.getSmartIdApiClient()
                .getCertificateStatus(relyingPartyInfo, certificateSession.getSessionCode());

        try (IgniteSemaphore containerSemaphore = containerSigningService.getIgnite().semaphore(sessionId, 1, true, true)) { // TODO: SIGA-424
            if (containerSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                processSmartIdCertificateStatusResponse(sessionId, certificateId, statusResponse);
            } else {
                log.error("Unprocessed SmartId certificate status response due to SIGA-424. Container session id: {}, Certificate session id: {}",
                        sessionId, certificateId);
            }
        }
    }

    private void processSmartIdCertificateStatusResponse(String sessionId, String certificateId, SmartIdStatusResponse statusResponse) {
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        if (session == null) {
            log.warn("Unable to process certificate status response. Container session expired: {}", sessionId);
            return;
        }
        CertificateSession certificateSession = session.getCertificateSession(certificateId);

        if (certificateSession != null) {
            certificateSession.setPollingStatus(RESULT);
            SessionStatus sessionStatus = certificateSession.getSessionStatus();
            if (statusResponse.getStatus() == SmartIdSessionStatus.OK) {
                SmartIdCertificate smartIdCertificate = statusResponse.getSmartIdCertificate();
                if (smartIdCertificate == null) {
                    sessionStatus.setStatusError(SMARTID_EXCEPTION.name(), "No certificate found from Smart-id response");
                } else {
                    session.addCertificate(smartIdCertificate.getDocumentNumber(), smartIdCertificate.getCertificate());
                    certificateSession.setDocumentNumber(statusResponse.getSmartIdCertificate().getDocumentNumber());
                }
            }
            sessionStatus.setStatus(statusResponse.getStatus().getSigaCertificateMessage());
            containerSigningService.getSessionService().update(session);
        } else {
            log.warn("Certificate session expired! Container session id: {}, Certificate session id: {}", sessionId, certificateId);
        }
    }

    public String getSmartIdSignatureStatus(String containerId, String signatureId) {
        Session session = containerSigningService.getSession(containerId);
        if (session == null || session.getSignatureSessionStatus(signatureId) == null) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". No data to sign with signature Id: " + signatureId);
        }
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        ensureSigningTypeIsSmartId(signatureSession);
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        String status = sessionStatus.getStatus();
        StatusError statusError = sessionStatus.getStatusError();
        if (sessionStatus.getProcessingStatus() == RESULT) {
            try {
                if (SmartIdSessionStatus.OK.getSigaSigningMessage().equals(status)) {
                    Signature signature = containerSigningService.finalizeSignature(session, signatureId, signatureSession.getSignature());
                    containerSigningService.addSignatureToSession(session, signature, signatureId);
                }
            } finally {
                session.removeSigningSession(signatureId);
                containerSigningService.getSessionService().update(session);
            }
            return status;
        } else {
            int maxProcessingAttempts = containerSigningService.getReprocessingProperties().getMaxProcessingAttempts();
            if (sessionStatus.getProcessingCounter() >= maxProcessingAttempts) {
                ErrorResponseCode errorResponseCode = EnumUtils.getEnum(ErrorResponseCode.class, statusError.getErrorCode(), INTERNAL_SERVER_ERROR);
                throw new SigaApiException(errorResponseCode, statusError.getErrorMessage());
            } else {
                return SmartIdSessionStatus.RUNNING.getSigaSigningMessage();
            }
        }
    }

    public void pollSmartIdSignatureStatus(String sessionId, String signatureId, Duration pollingDelay) {
        Runnable pollingRunnable = () -> {
            // If semaphore is not acquired it will be re-processed by SessionStatusReprocessingService
            IgniteSemaphore signatureSemaphore = containerSigningService.getIgnite().semaphore(signatureId, 1, true, true);
            if (signatureSemaphore.tryAcquire()) {
                try (signatureSemaphore) {
                    pollSignatureStatus(sessionId, signatureId);
                } catch (Exception ex) {
                    setPollingException(sessionId, signatureId, ex);
                } finally {
                    // Semaphore release conditions 1) Normal execution 2) Exception occurs 3) Ignite node leaves topology
                    containerSigningService.getSigaEventLogger().logEvents();
                    log.debug("Status polling unlocked for signature id: {}", signatureId);
                }
            } else {
                log.debug("Status polling semaphore not acquired for signature id: {}", signatureId);
            }
        };
        DelegatingSecurityContextRunnable delegatingRunnable = new DelegatingSecurityContextRunnable(pollingRunnable);
        CompletableFuture.runAsync(delegatingRunnable, delayedExecutor(pollingDelay.toMillis(), MILLISECONDS, containerSigningService.getTaskExecutor()));
    }

    private void pollSignatureStatus(String sessionId, String signatureId) {
        log.debug("Status polling locked for signature id: {}", signatureId);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        if (signatureSession == null) {
            log.warn("Unable to poll signature status. Container {} signature session {} is expired!", sessionId, signatureId);
            return;
        }
        RelyingPartyInfo relyingPartyInfo = signatureSession.getRelyingPartyInfo();
        String sessionCode = signatureSession.getSessionCode();
        SmartIdStatusResponse statusResponse = containerSigningService.getSmartIdApiClient().getSignatureStatus(relyingPartyInfo, sessionCode);

        try (IgniteSemaphore containerSemaphore = containerSigningService.getIgnite().semaphore(sessionId, 1, true, true)) { // TODO: SIGA-424
            if (containerSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                processSmartIdSignatureStatusResponse(sessionId, signatureId, statusResponse);
            } else {
                log.error("Unprocessed SmartId signature status response due to SIGA-424. Container session id: {}, Signature session id: {}",
                        sessionId, signatureId);
            }
        }
    }

    private void processSmartIdSignatureStatusResponse(String sessionId, String signatureId, SmartIdStatusResponse sessionResponse) {
        log.debug("Processing response for signature: {}", signatureId);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        if (session == null) {
            log.warn("Unable to process signature status response. Container session expired: {}", sessionId);
            return;
        }
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        if (signatureSession != null && signatureSession.getDataToSign() != null) {
            signatureSession.setPollingStatus(RESULT);
            signatureSession.setSignature(sessionResponse.getSignature());
            SmartIdSessionStatus sidStatus = sessionResponse.getStatus();
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            sessionStatus.setStatus(sidStatus.getSigaSigningMessage());
            sessionStatus.setStatusError(null);
            containerSigningService.getSessionService().update(session);
        } else {
            log.warn("Signature session expired! Container session id: {}, Signature session id: {}", sessionId, signatureId);
        }
    }

    private void setPollingException(String sessionId, String statusSessionId, Exception ex) {
        log.error("SmartId status polling exception. Container session id: {}, Status session id: {}",
                sessionId, statusSessionId, ex);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        SignatureSession signatureSession = session.getSignatureSession(statusSessionId);
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        sessionStatus.setProcessingStatus(EXCEPTION);
        sessionStatus.setStatusError(StatusError.builder()
                .errorCode(INTERNAL_SERVER_ERROR.name()) // TODO: Exception to error code map
                .errorMessage(ex.getMessage())
                .build());
        containerSigningService.getSessionService().update(session);
    }

    private void ensureSigningTypeIsSmartId(SignatureSession signatureSession) {
        SigningType signingType = signatureSession.getSigningType();
        if (signingType != SMART_ID) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + " for signing type: " + signingType);
        }
    }
}
