package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionLocks;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Predicate;

import static java.time.Duration.ZERO;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStatusReprocessingService {
    @NonNull
    private final ThreadPoolTaskExecutor taskExecutor;
    @NonNull
    private final SessionStatusScanner sessionStatusScanner;
    @NonNull
    private final ContainerSigningServiceSelector containerSigningServiceSelector;
    @NonNull
    private final SessionService sessionService;
    @NonNull
    private final SessionLockRegistry sessionLockRegistry;
    @NonNull
    private final SessionStatusReprocessingProperties reprocessingProperties;

    @Scheduled(fixedRateString = "${siga.status-reprocessing.fixed-rate:5000}", initialDelayString = "${siga.status-reprocessing.initial-delay:5000}")
    public void processFailedStatusRequests() {
        StatusReprocessingFilter filter = StatusReprocessingFilter.from(
                reprocessingProperties.maxProcessingAttempts(),
                reprocessingProperties.processingTimeout(),
                reprocessingProperties.exceptionTimeout());
        sessionStatusScanner.scanSignatureSessions(filter,
                sessionId -> processFailedContainerSession(filter, sessionId));
    }

    void processFailedContainerSession(StatusReprocessingFilter filter, String sessionId) {
        SessionLocks.tryRun(sessionLockRegistry.obtain(sessionId), () -> {
            Session session;
            try {
                session = sessionService.peekContainerBySessionId(sessionId);
            } catch (ResourceNotFoundException e) {
                // Status-index entry for a session that has already expired or been deleted.
                // The compactor will reconcile the orphan; do not let it abort the rest of the batch.
                log.debug("Skipping orphan signature-status index entry: {}", sessionId);
                return;
            }
            ContainerSigningService signingService = containerSigningServiceSelector
                    .getContainerSigningServiceFor(session);
            if (signingService == null) {
                log.warn("No compatible service found for reprocessing session: {}", sessionId);
                return;
            }

            Map<String, SignatureSession> signatureSessions = session.getSignatureSessions();
            signatureSessions.entrySet().stream().filter(applySignatureStatusFilter(filter)).forEach(entry -> {
                String signatureSessionId = entry.getKey();
                SigningType signingType = entry.getValue().getSigningType();
                log.info("Reprocessing failed signature status request: {}, Session status: {},",
                        signatureSessionId, entry.getValue().getSessionStatus());
                if (signingType == SigningType.SMART_ID) {
                    signingService.pollSmartIdSignatureStatus(sessionId, signatureSessionId, ZERO);
                } else if (signingType == SigningType.MOBILE_ID) {
                    signingService.pollMobileIdSignatureStatus(sessionId, signatureSessionId, ZERO);
                }
            });
        });
    }

    @Scheduled(fixedRateString = "${siga.status-reprocessing.fixed-rate:5000}", initialDelayString = "${siga.status-reprocessing.initial-delay:5000}")
    public void processFailedCertificateStatusRequests() {
        StatusReprocessingFilter filter = StatusReprocessingFilter.from(
                reprocessingProperties.maxProcessingAttempts(),
                reprocessingProperties.processingTimeout(),
                reprocessingProperties.exceptionTimeout());
        sessionStatusScanner.scanCertificateSessions(filter,
                sessionId -> processFailedCertificateStatusRequest(filter, sessionId));
    }

    void processFailedCertificateStatusRequest(StatusReprocessingFilter filter, String sessionId) {
        SessionLocks.tryRun(sessionLockRegistry.obtain(sessionId), () -> {
            Session session;
            try {
                session = sessionService.peekContainerBySessionId(sessionId);
            } catch (ResourceNotFoundException e) {
                log.debug("Skipping orphan certificate-status index entry: {}", sessionId);
                return;
            }
            ContainerSigningService signingService = containerSigningServiceSelector
                    .getContainerSigningServiceFor(session);
            if (signingService == null) {
                log.warn("No compatible service found for reprocessing session: {}", sessionId);
                return;
            }

            Map<String, CertificateSession> certificateSessions = session.getCertificateSessions();
            certificateSessions.entrySet().stream().filter(applyCertificateStatusFilter(filter)).forEach(entry -> {
                String certificateSessionId = entry.getKey();
                log.info("Reprocessing failed certificate status request: {}, Session status: {},",
                        certificateSessionId, entry.getValue().getSessionStatus());
                signingService.pollSmartIdCertificateStatus(sessionId, certificateSessionId, ZERO);
            });
        });
    }

    @PreDestroy
    @SneakyThrows
    public void onDestroy() {
        long timeout = 300;
        long currentCount = 0;
        log.info("Graceful shutdown in progress!");
        while (taskExecutor.getActiveCount() != 0 && currentCount++ <= timeout) {
            log.info("Nr. of active status polling jobs left: {}. Timeout in: {}", taskExecutor.getActiveCount(),
                    timeout - currentCount);
            Thread.sleep(1000);
        }
        log.info("Continuing shutdown!");
    }

    private static Predicate<Map.Entry<String, SignatureSession>> applySignatureStatusFilter(StatusReprocessingFilter filter) {
        return entry -> SigningType.isPollable(entry.getValue().getSigningType())
                && isDueForReprocessing(filter, entry.getValue().getSessionStatus());
    }

    private static Predicate<Map.Entry<String, CertificateSession>> applyCertificateStatusFilter(StatusReprocessingFilter filter) {
        return entry -> isDueForReprocessing(filter, entry.getValue().getSessionStatus());
    }

    private static boolean isDueForReprocessing(StatusReprocessingFilter filter, SessionStatus status) {
        return status.isDueForReprocessing(filter.processingCutoff(), filter.exceptionCutoff(),
                filter.maxProcessingRetries());
    }
}
