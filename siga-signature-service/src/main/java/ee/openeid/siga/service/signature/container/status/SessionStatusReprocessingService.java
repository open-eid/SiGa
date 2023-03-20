package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.session.CacheName;
import ee.openeid.siga.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.function.Predicate;

import static java.time.Duration.ZERO;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SessionStatusReprocessingProperties.class)
public class SessionStatusReprocessingService {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Ignite ignite;
    private final ContainerSigningServiceSelector containerSigningServiceSelector;
    private final SessionService sessionService;
    private final SessionStatusReprocessingProperties reprocessingProperties;

    @Scheduled(fixedRateString = "${siga.status-reprocessing.fixed-rate:5000}", initialDelayString = "${siga.status-reprocessing.initial-delay:5000}")
    public void processFailedStatusRequests() {
        SignatureStatusRequestFilter filter = new SignatureStatusRequestFilter(
                reprocessingProperties.getMaxProcessingAttempts(),
                reprocessingProperties.getProcessingTimeout(), reprocessingProperties.getExceptionTimeout());
        ScanQuery<String, Map<String, BinaryObject>> query = new ScanQuery<>(filter);
        try (QueryCursor<String> queryCursor = ignite.getOrCreateCache(CacheName.SIGNATURE_SESSION.name())
                .withKeepBinary()
                .query(query, new SessionIdQueryTransformer())) {
            queryCursor.forEach(sessionId -> processFailedContainerSession(filter, sessionId));
        }
    }

    void processFailedContainerSession(SignatureStatusRequestFilter filter, String sessionId) {
        Session session = sessionService.getContainerBySessionId(sessionId);
        ContainerSigningService containerSigningService = containerSigningServiceSelector
                .getContainerSigningServiceFor(session);

        if (containerSigningService == null) {
            log.warn("No compatible service found for reprocessing session: {}", sessionId);
            return;
        }

        Map<String, SignatureSession> signatureSessions = session.getSignatureSessions();
        signatureSessions.entrySet().stream().filter(applySignatureStatusRequestFilter(filter)).forEach(s -> {
            String signatureSessionId = s.getKey();
            SigningType signingType = s.getValue().getSigningType();
            log.info("Reprocessing failed signature status request: {}, Session status: {},",
                    signatureSessionId, s.getValue().getSessionStatus());

            if (signingType == SigningType.SMART_ID) {
                containerSigningService.pollSmartIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            } else if (signingType == SigningType.MOBILE_ID) {
                containerSigningService.pollMobileIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            }
        });
    }

    @Scheduled(fixedRateString = "${siga.status-reprocessing.fixed-rate:5000}", initialDelayString = "${siga.status-reprocessing.initial-delay:5000}")
    public void processFailedCertificateStatusRequests() {
        CertificateStatusRequestFilter filter = new CertificateStatusRequestFilter(
                reprocessingProperties.getMaxProcessingAttempts(),
                reprocessingProperties.getProcessingTimeout(), reprocessingProperties.getExceptionTimeout());
        ScanQuery<String, Map<String, BinaryObject>> query = new ScanQuery<>(filter);
        try (QueryCursor<String> queryCursor = ignite.getOrCreateCache(CacheName.CERTIFICATE_SESSION.name())
                .withKeepBinary()
                .query(query, new SessionIdQueryTransformer())) {
            queryCursor.forEach(sessionId -> processFailedCertificateStatusRequest(filter, sessionId));
        }
    }

    void processFailedCertificateStatusRequest(CertificateStatusRequestFilter filter, String sessionId) {
        Session session = sessionService.getContainerBySessionId(sessionId);
        ContainerSigningService containerSigningService = containerSigningServiceSelector
                .getContainerSigningServiceFor(session);

        if (containerSigningService == null) {
            log.warn("No compatible service found for reprocessing session: {}", sessionId);
            return;
        }

        Map<String, CertificateSession> certificateSessions = session.getCertificateSessions();
        certificateSessions.entrySet().stream().filter(applyCertificateStatusRequestFilter(filter)).forEach(s -> {
            String certificateSessionId = s.getKey();
            log.info("Reprocessing failed certificate status request: {}, Session status: {},",
                    certificateSessionId, s.getValue().getSessionStatus());

            containerSigningService.pollSmartIdCertificateStatus(sessionId, certificateSessionId, ZERO);
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

    private static Predicate<Map.Entry<String, SignatureSession>> applySignatureStatusRequestFilter(
            SignatureStatusRequestFilter filter) {
        return s -> {
            SignatureSession signatureSession = s.getValue();
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            return SignatureStatusRequestFilter.isApplyFilter(filter, sessionStatus.getProcessingStatus(),
                    sessionStatus.getProcessingStatusTimestamp(),
                    sessionStatus.getProcessingCounter());
        };
    }

    private static Predicate<Map.Entry<String, CertificateSession>> applyCertificateStatusRequestFilter(
            CertificateStatusRequestFilter filter) {
        return s -> {
            CertificateSession certificateSession = s.getValue();
            SessionStatus sessionStatus = certificateSession.getSessionStatus();
            return CertificateStatusRequestFilter.isApplyFilter(filter, sessionStatus.getProcessingStatus(),
                    sessionStatus.getProcessingStatusTimestamp(),
                    sessionStatus.getProcessingCounter());
        };
    }
}
