package ee.openeid.siga.service.signature.container.status;

import static java.time.Duration.ZERO;

import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.PreDestroy;

import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.session.CacheName;
import ee.openeid.siga.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SessionStatusReprocessingProperties.class)
public class SessionStatusReprocessingService {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Ignite ignite;
    private final AsicContainerSigningService asicContainerSigningService;
    private final HashcodeContainerSigningService hashcodeContainerSigningService;
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
        Map<String, SignatureSession> signatureSessions = session.getSignatureSessions();

        signatureSessions.entrySet().stream().filter(applySignatureStatusRequestFilter(filter)).forEach(s -> {
            String signatureSessionId = s.getKey();
            SignatureSession signatureSession = s.getValue();
            processFailedSignatureSession(session, signatureSessionId, signatureSession.getSigningType(),
                    signatureSession.getSessionStatus());
        });
    }

    void processFailedSignatureSession(Session session, String signatureSessionId, SigningType signingType,
            SessionStatus sessionStatus) {
        String sessionId = session.getSessionId();
        log.info("Reprocessing failed signature status request: {}, Session status: {},", signatureSessionId,
                sessionStatus);
        if (signingType == SigningType.SMART_ID) {
            if (session instanceof AsicContainerSession) {
                asicContainerSigningService.pollSmartIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            } else if (session instanceof HashcodeContainerSession) {
                hashcodeContainerSigningService.pollSmartIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            }
        } else if (signingType == SigningType.MOBILE_ID) {
            if (session instanceof AsicContainerSession) {
                asicContainerSigningService.pollMobileIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            } else if (session instanceof HashcodeContainerSession) {
                hashcodeContainerSigningService.pollMobileIdSignatureStatus(sessionId, signatureSessionId, ZERO);
            }
        }
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
        Map<String, CertificateSession> certificateSessions = session.getCertificateSessions();

        certificateSessions.entrySet().stream().filter(applyCertificateStatusRequestFilter(filter)).forEach(s -> {
            String certificateSessionId = s.getKey();
            if (session instanceof AsicContainerSession) {
                asicContainerSigningService.pollSmartIdCertificateStatus(sessionId, certificateSessionId, ZERO);
            } else if (session instanceof HashcodeContainerSession) {
                hashcodeContainerSigningService.pollSmartIdCertificateStatus(sessionId, certificateSessionId, ZERO);
            }
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
