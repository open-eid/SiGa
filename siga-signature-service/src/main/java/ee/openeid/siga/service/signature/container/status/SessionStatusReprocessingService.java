package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import java.util.stream.Collectors;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INTERNAL_SERVER_ERROR;
import static ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION;
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
    // Empty while the mobileId/smartId profile is inactive: Spring injects an empty list for a
    // collection parameter of the only constructor.
    @NonNull
    private final List<MobileIdSigningDelegate<?>> mobileIdDelegates;
    @NonNull
    private final List<SmartIdSigningDelegate<?>> smartIdDelegates;

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

            SmartIdSigningDelegate<?> smartIdDelegate =
                    delegateFor(smartIdDelegates, SmartIdSigningDelegate::getContainerSigningService, signingService);
            MobileIdSigningDelegate<?> mobileIdDelegate =
                    delegateFor(mobileIdDelegates, MobileIdSigningDelegate::getContainerSigningService, signingService);

            Map<String, SignatureSession> signatureSessions = session.getSignatureSessions();
            Map<Boolean, List<Map.Entry<String, SignatureSession>>> dueBySigningEnabled = signatureSessions.entrySet().stream()
                    .filter(applySignatureStatusFilter(filter))
                    .collect(Collectors.partitioningBy(entry ->
                            (entry.getValue().getSigningType() == SigningType.SMART_ID ? smartIdDelegate : mobileIdDelegate) != null));
            // Signing type not enabled on this node: fail the status attempt like a polling exception does,
            // so the entry leaves the queue once maxProcessingAttempts is reached. Persisted before any
            // async poll is started, so this session copy cannot overwrite a poll result.
            List<Map.Entry<String, SignatureSession>> notEnabled = dueBySigningEnabled.get(false);
            if (!notEnabled.isEmpty()) {
                notEnabled.forEach(entry -> markSigningNotEnabled(sessionId, entry.getKey(),
                        entry.getValue().getSigningType(), entry.getValue().getSessionStatus()));
                sessionService.update(session);
            }
            dueBySigningEnabled.get(true).forEach(entry -> {
                String signatureSessionId = entry.getKey();
                SigningType signingType = entry.getValue().getSigningType();
                log.info("Reprocessing failed signature status request: {}, Session status: {},",
                        signatureSessionId, entry.getValue().getSessionStatus());
                if (signingType == SigningType.SMART_ID) {
                    smartIdDelegate.pollSmartIdSignatureStatus(sessionId, signatureSessionId, ZERO);
                } else if (signingType == SigningType.MOBILE_ID) {
                    mobileIdDelegate.pollMobileIdSignatureStatus(sessionId, signatureSessionId, ZERO);
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
            SmartIdSigningDelegate<?> delegate =
                    delegateFor(smartIdDelegates, SmartIdSigningDelegate::getContainerSigningService, signingService);

            Map<String, CertificateSession> certificateSessions = session.getCertificateSessions();
            if (delegate == null) {
                List<Map.Entry<String, CertificateSession>> dueEntries = certificateSessions.entrySet().stream()
                        .filter(applyCertificateStatusFilter(filter))
                        .toList();
                if (!dueEntries.isEmpty()) {
                    dueEntries.forEach(entry -> markSigningNotEnabled(sessionId, entry.getKey(),
                            SigningType.SMART_ID, entry.getValue().getSessionStatus()));
                    sessionService.update(session);
                }
                return;
            }
            certificateSessions.entrySet().stream().filter(applyCertificateStatusFilter(filter)).forEach(entry -> {
                String certificateSessionId = entry.getKey();
                log.info("Reprocessing failed certificate status request: {}, Session status: {},",
                        certificateSessionId, entry.getValue().getSessionStatus());
                delegate.pollSmartIdCertificateStatus(sessionId, certificateSessionId, ZERO);
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

    private static void markSigningNotEnabled(String sessionId, String statusSessionId, SigningType signingType,
                                              SessionStatus sessionStatus) {
        log.error("{} status polling exception, signing not enabled. Session id: {}, Status session id: {}",
                signingType, sessionId, statusSessionId);
        sessionStatus.setProcessingStatus(EXCEPTION);
        sessionStatus.setStatusError(INTERNAL_SERVER_ERROR.name(), signingType + " signing not enabled");
    }

    private static <D> D delegateFor(List<D> delegates, Function<D, ContainerSigningService> signingServiceOf,
                                     ContainerSigningService signingService) {
        return delegates.stream()
                .filter(delegate -> signingServiceOf.apply(delegate) == signingService)
                .findFirst()
                .orElse(null);
    }

}
