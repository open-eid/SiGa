package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.Lock;

import static java.time.Duration.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Isolates the SPI lock contract for {@link SessionStatusReprocessingService}. The existing
 * end-to-end test ({@code SessionStatusReprocessingServiceTest}) runs against a real Ignite
 * scanner; without this unit-level pin a refactor that drops the {@code SessionLocks.tryRun}
 * wrap (or routes around the {@link SessionLockRegistry}) would still pass that test but break
 * mutual exclusion across nodes on Redis.
 */
@ExtendWith(MockitoExtension.class)
class SessionStatusReprocessingServiceLockTest {

    private static final String SESSION_ID = "v1_svc_session-1";
    private static final String SIGNATURE_ID = "signature-1";

    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private SessionStatusScanner sessionStatusScanner;
    @Mock
    private ContainerSigningServiceSelector signingServiceSelector;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionLockRegistry sessionLockRegistry;
    @Mock
    private ContainerSigningService signingService;
    @Mock
    private Lock sessionLock;

    private SessionStatusReprocessingProperties reprocessingProperties;
    private StatusReprocessingFilter filter;
    private SessionStatusReprocessingService service;

    @BeforeEach
    void setUp() {
        reprocessingProperties = new SessionStatusReprocessingProperties(
                10, Duration.ofSeconds(30), Duration.ofSeconds(30));

        filter = StatusReprocessingFilter.from(
                reprocessingProperties.maxProcessingAttempts(),
                reprocessingProperties.processingTimeout(),
                reprocessingProperties.exceptionTimeout());

        service = new SessionStatusReprocessingService(
                taskExecutor, sessionStatusScanner, signingServiceSelector,
                sessionService, sessionLockRegistry, reprocessingProperties);
    }

    @Test
    void shouldObtainLockBySessionId_AndCallSigningService_WhenLockAcquired() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(true);
        HashcodeContainerSession session = sessionWithStaleSignature();
        when(sessionService.peekContainerBySessionId(SESSION_ID)).thenReturn(session);
        when(signingServiceSelector.getContainerSigningServiceFor(session)).thenReturn(signingService);

        service.processFailedContainerSession(filter, SESSION_ID);

        // Lock obtained by sessionId — never by signatureId at this layer; signature-keyed locks
        // belong to the delegate.
        verify(sessionLockRegistry).obtain(SESSION_ID);
        verify(sessionLockRegistry, never()).obtain(SIGNATURE_ID);
        // peek (not get) — must NOT slide TTL during reprocessing.
        verify(sessionService).peekContainerBySessionId(SESSION_ID);
        // Stale MID signature gets re-polled with ZERO delay.
        verify(signingService).pollMobileIdSignatureStatus(eq(SESSION_ID), eq(SIGNATURE_ID), eq(ZERO));
        verify(sessionLock).unlock();
    }

    @Test
    void shouldSkipReprocessing_WhenSessionLockHeldByAnotherInstance() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(false);

        service.processFailedContainerSession(filter, SESSION_ID);

        // Another node owns the lock — this node must not look up the session and must not invoke
        // any signing service. SessionLocks.tryRun also must not release a lock it didn't hold.
        verify(sessionLockRegistry).obtain(SESSION_ID);
        verify(sessionService, never()).peekContainerBySessionId(any());
        verify(signingServiceSelector, never()).getContainerSigningServiceFor(any());
        verify(sessionLock, never()).unlock();
    }

    @Test
    void shouldSkipOrphan_WhenPeekThrowsResourceNotFound() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(true);
        when(sessionService.peekContainerBySessionId(SESSION_ID))
                .thenThrow(new ResourceNotFoundException("expired"));

        service.processFailedContainerSession(filter, SESSION_ID);

        // Orphan index entry — must short-circuit cleanly. The compactor reconciles the index;
        // this code path must not throw and must release the lock.
        verify(signingServiceSelector, never()).getContainerSigningServiceFor(any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldSkipReprocessing_WhenNoSigningServiceMatchesSession() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(true);
        HashcodeContainerSession session = sessionWithStaleSignature();
        when(sessionService.peekContainerBySessionId(SESSION_ID)).thenReturn(session);
        when(signingServiceSelector.getContainerSigningServiceFor(session)).thenReturn(null);

        service.processFailedContainerSession(filter, SESSION_ID);

        // Session exists but no matching service — must not throw NPE on signingService.pollX().
        verify(sessionLock).unlock();
    }

    @Test
    void shouldObtainLockBySessionId_WhenProcessingFailedCertificateRequest() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(true);
        HashcodeContainerSession session = sessionWithStaleCertificate();
        when(sessionService.peekContainerBySessionId(SESSION_ID)).thenReturn(session);
        when(signingServiceSelector.getContainerSigningServiceFor(session)).thenReturn(signingService);

        service.processFailedCertificateStatusRequest(filter, SESSION_ID);

        verify(sessionLockRegistry).obtain(SESSION_ID);
        verify(signingService).pollSmartIdCertificateStatus(eq(SESSION_ID), eq("cert-1"), eq(ZERO));
        verify(sessionLock).unlock();
    }

    private static HashcodeContainerSession sessionWithStaleSignature() {
        ee.openeid.siga.common.session.SessionStatus stale = ee.openeid.siga.common.session.SessionStatus.builder()
                .processingStatus(ee.openeid.siga.common.session.ProcessingStatus.PROCESSING)
                .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
        SignatureSession signature = SignatureSession.builder()
                .signingType(SigningType.MOBILE_ID)
                .sessionStatus(stale)
                .build();
        HashcodeContainerSession session = HashcodeContainerSession.builder()
                .sessionId(SESSION_ID)
                .clientName("c").serviceName("s").serviceUuid("u")
                .build();
        session.addSignatureSession(SIGNATURE_ID, signature);
        return session;
    }

    private static HashcodeContainerSession sessionWithStaleCertificate() {
        ee.openeid.siga.common.session.SessionStatus stale = ee.openeid.siga.common.session.SessionStatus.builder()
                .processingStatus(ee.openeid.siga.common.session.ProcessingStatus.PROCESSING)
                .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
        ee.openeid.siga.common.session.CertificateSession certificate =
                ee.openeid.siga.common.session.CertificateSession.builder()
                        .sessionStatus(stale)
                        .build();
        HashcodeContainerSession session = HashcodeContainerSession.builder()
                .sessionId(SESSION_ID)
                .clientName("c").serviceName("s").serviceUuid("u")
                .build();
        session.addCertificateSession("cert-1", certificate);
        return session;
    }
}
