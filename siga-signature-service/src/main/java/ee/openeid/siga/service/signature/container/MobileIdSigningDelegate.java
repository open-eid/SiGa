package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SessionStatus.StatusError;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MobileIdSessionStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdStatusResponse;
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
import static ee.openeid.siga.common.model.SigningType.MOBILE_ID;
import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;
import static ee.openeid.siga.service.signature.container.ContainerSigningService.UNABLE_TO_FINALIZE_SIGNATURE;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
@RequiredArgsConstructor
public class MobileIdSigningDelegate {
    private final ContainerSigningService containerSigningService;

    public static RelyingPartyInfo getRelyingPartyInfo() {
        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return RelyingPartyInfo.builder()
                .name(sigaUserDetails.getSkRelyingPartyName())
                .uuid(sigaUserDetails.getSkRelyingPartyUuid())
                .build();
    }

    public SigningChallenge startMobileIdSigning(String containerId, MobileIdInformation mobileIdInformation, SignatureParameters signatureParameters) {
        Session session = containerSigningService.getSession(containerId);
        containerSigningService.verifySigningObjectExistence(session);
        RelyingPartyInfo relyingPartyInfo = getRelyingPartyInfo();
        X509Certificate certificate = containerSigningService.getMobileIdApiClient().getCertificate(relyingPartyInfo, mobileIdInformation);
        signatureParameters.setSigningCertificate(certificate);
        DataToSign dataToSign = containerSigningService.buildDataToSign(session, signatureParameters);

        InitMidSignatureResponse initMidSignatureResponse = containerSigningService.getMobileIdApiClient().initMobileSigning(relyingPartyInfo, dataToSign, mobileIdInformation);

        String generatedSignatureId = UUIDGenerator.generateUUID();
        SignatureSession signatureSession = SignatureSession.builder()
                .relyingPartyInfo(relyingPartyInfo)
                .dataToSign(dataToSign)
                .signingType(MOBILE_ID)
                .sessionCode(initMidSignatureResponse.getSessionCode())
                .dataFilesHash(containerSigningService.generateDataFilesHash(session))
                .build();

        session.addSignatureSession(generatedSignatureId, signatureSession);
        pollMobileIdSignatureStatus(session.getSessionId(), generatedSignatureId,
                containerSigningService.getMobileIdConfigurationProperties().getStatusPollingDelay());
        containerSigningService.getSessionService().update(session);

        return SigningChallenge.builder()
                .challengeId(initMidSignatureResponse.getChallengeId())
                .generatedSignatureId(generatedSignatureId)
                .build();
    }

    public String getMobileIdSignatureStatus(String containerId, String signatureId) {
        Session session = containerSigningService.getSession(containerId);
        if (session == null || session.getSignatureSessionStatus(signatureId) == null) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + ". No data to sign with signature Id: " + signatureId);
        }
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        ensureSigningTypeIsMobileId(signatureSession);
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        String status = sessionStatus.getStatus();
        StatusError statusError = sessionStatus.getStatusError();

        if (sessionStatus.getProcessingStatus() == RESULT) {
            try {
                if (MobileIdSessionStatus.SIGNATURE.name().equals(status)) {
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
            boolean isMaxPollingAttempts = sessionStatus.getProcessingCounter() >= maxProcessingAttempts;
            if (isMaxPollingAttempts) {
                ErrorResponseCode errorResponseCode = EnumUtils.getEnum(ErrorResponseCode.class, statusError.getErrorCode(), INTERNAL_SERVER_ERROR);
                throw new SigaApiException(errorResponseCode, statusError.getErrorMessage());
            } else {
                return MobileIdSessionStatus.OUTSTANDING_TRANSACTION.name();
            }
        }
    }

    public void pollMobileIdSignatureStatus(String sessionId, String signatureId, Duration pollingDelay) {
        Runnable pollingRunnable = () -> {
            // If semaphore is not acquired it will be re-processed by SessionStatusReprocessingService
            IgniteSemaphore semaphore = containerSigningService.getIgnite().semaphore(signatureId, 1, true, true);
            if (semaphore.tryAcquire()) {
                try (semaphore) {
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
        MobileIdStatusResponse statusResponse = containerSigningService.getMobileIdApiClient().getSignatureStatus(relyingPartyInfo, sessionCode);

        try (IgniteSemaphore containerSemaphore = containerSigningService.getIgnite().semaphore(sessionId, 1, true, true)) { // TODO: SIGA-424
            if (containerSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                processMobileIdStatusResponse(sessionId, signatureId, statusResponse);
            } else {
                log.error("Unprocessed MobileId signature status response due to SIGA-424. Container session id: {}, Signature session id: {}",
                        sessionId, signatureId);
            }
        }
    }

    private void processMobileIdStatusResponse(String sessionId, String signatureId, MobileIdStatusResponse mobileIdStatusResponse) {
        log.debug("Processing MobileId response for signature: {}", signatureId);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        if (session == null) {
            log.warn("Unable to process signature status response. Container session expired: {}", sessionId);
            return;
        }
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        if (signatureSession != null && signatureSession.getDataToSign() != null) {
            signatureSession.setSignature(mobileIdStatusResponse.getSignature());
            signatureSession.setPollingStatus(RESULT);
            signatureSession.getSessionStatus().setStatusError(null);
            MobileIdSessionStatus mobileIdSessionStatus = mobileIdStatusResponse.getStatus();
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            sessionStatus.setStatus(mobileIdSessionStatus.name());
            containerSigningService.getSessionService().update(session);
        }
    }

    private void setPollingException(String sessionId, String signatureId, Exception ex) {
        log.error("MobileId status polling exception. Session id: {}, Signature id: {}",
                sessionId, signatureId, ex);
        Session session = containerSigningService.getSessionService().getContainerBySessionId(sessionId);
        SignatureSession signatureSession = session.getSignatureSession(signatureId);
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        sessionStatus.setProcessingStatus(EXCEPTION);
        sessionStatus.setStatusError(StatusError.builder()
                .errorCode(INTERNAL_SERVER_ERROR.name()) // TODO: Exception to error code map
                .errorMessage(ex.getMessage())
                .build());
        containerSigningService.getSessionService().update(session);
    }

    private void ensureSigningTypeIsMobileId(SignatureSession signatureSession) {
        SigningType signingType = signatureSession.getSigningType();
        if (signingType != MOBILE_ID) {
            throw new InvalidSessionDataException(UNABLE_TO_FINALIZE_SIGNATURE + " for signing type: " + signingType);
        }
    }
}
