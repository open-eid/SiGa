package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.SmartIdStatusResponse;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors {@link MobileIdSigningDelegateLockRenewalTest} for Smart-ID. The same 5-second timed
 * container lock contract applies to {@link SmartIdSigningDelegate#pollSmartIdSignatureStatus}
 * and {@link SmartIdSigningDelegate#pollSmartIdCertificateStatus}; if either drops the renewal
 * timing or co-locates polling and container locks on the same key, concurrent renewal threads
 * can deadlock the response write.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // setUp stubs the full success path; the
// early-return tests (skip API call, container-lock timeout) exercise only a subset.
class SmartIdSigningDelegateLockRenewalTest {

    private static final String SIGNATURE_ID = "signature1";
    private static final String CERTIFICATE_ID = "certificate1";
    private static final String SESSION_ID = "v1_svc_session-1";

    @Mock
    private ContainerSigningService containerSigningService;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionLockRegistry sessionLockRegistry;
    @Mock
    private SmartIdApiClient smartIdApiClient;
    @Mock
    private SigaEventLogger sigaEventLogger;
    @Mock
    private Lock pollingLock;
    @Mock
    private Lock containerLock;

    private ThreadPoolTaskExecutor taskExecutor;
    private SmartIdSigningDelegate delegate;

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.initialize();

        when(containerSigningService.getTaskExecutor()).thenReturn(taskExecutor);
        when(containerSigningService.getSessionLockRegistry()).thenReturn(sessionLockRegistry);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);
        when(containerSigningService.getSmartIdApiClient()).thenReturn(smartIdApiClient);
        when(containerSigningService.getSigaEventLogger()).thenReturn(sigaEventLogger);

        when(sessionLockRegistry.obtain(SIGNATURE_ID)).thenReturn(pollingLock);
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(containerLock);

        when(sessionService.getContainerBySessionId(SESSION_ID)).thenReturn(activeSignatureSession());

        SmartIdStatusResponse response = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.RUNNING)
                .build();
        when(smartIdApiClient.getSignatureStatus(any(), any())).thenReturn(response);

        delegate = new SmartIdSigningDelegate(containerSigningService);
    }

    @AfterEach
    void shutdownExecutor() {
        taskExecutor.shutdown();
    }

    @Test
    void shouldObtainPollingLockBySignatureId_AndRenewWithContainerLockUnder5sTimeout_WhenSignatureStatusPolled()
            throws InterruptedException {
        when(pollingLock.tryLock()).thenReturn(true);
        when(containerLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        delegate.pollSmartIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        verify(sessionLockRegistry, timeout(2_000)).obtain(SIGNATURE_ID);
        verify(sessionLockRegistry, timeout(2_000)).obtain(SESSION_ID);
        verify(containerLock, timeout(2_000)).tryLock(5L, TimeUnit.SECONDS);
        verify(pollingLock, timeout(2_000)).unlock();
        verify(containerLock, timeout(2_000)).unlock();
    }

    @Test
    void shouldSkipApiCall_WhenSignaturePollingLockNotAcquired() {
        when(pollingLock.tryLock()).thenReturn(false);

        delegate.pollSmartIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        verify(pollingLock, timeout(2_000)).tryLock();
        verify(smartIdApiClient, never()).getSignatureStatus(any(), any());
        verify(sessionLockRegistry, never()).obtain(SESSION_ID);
        verify(pollingLock, never()).unlock();
    }

    @Test
    void shouldSkipSessionUpdate_WhenContainerLockTimedAcquisitionFails() throws InterruptedException {
        when(pollingLock.tryLock()).thenReturn(true);
        when(containerLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        delegate.pollSmartIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        verify(containerLock, timeout(2_000)).tryLock(5L, TimeUnit.SECONDS);
        verify(sessionService, timeout(2_000).times(0)).update(any());
        verify(pollingLock, timeout(2_000)).unlock();
    }

    @Test
    void shouldReleasePollingLockAndRecordException_WhenSmartIdApiCallThrows() {
        when(pollingLock.tryLock()).thenReturn(true);
        // SID API call inside the polling Runnable throws — exercise catch/setPollingException.
        // The polling lock must still be released (try/finally in SessionLocks.tryRun); without
        // this, the signatureId would stay locked until the next reprocessing cycle.
        when(smartIdApiClient.getSignatureStatus(any(), any()))
                .thenThrow(new RuntimeException("SID API down"));

        delegate.pollSmartIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        verify(sessionService, timeout(2_000)).update(argThat(s ->
                s.getSignatureSession(SIGNATURE_ID) != null
                        && s.getSignatureSession(SIGNATURE_ID).getSessionStatus().getProcessingStatus()
                        == ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION
                        && "SID API down".equals(
                        s.getSignatureSession(SIGNATURE_ID).getSessionStatus().getStatusError().getErrorMessage())
        ));
        verify(pollingLock, timeout(2_000)).unlock();
    }

    @Test
    void shouldObtainPollingLockByCertificateId_AndRenewWithContainerLockUnder5sTimeout_WhenCertificateStatusPolled()
            throws InterruptedException {
        // Symmetric contract for the certificate polling path — the polling lock key here is
        // the certificateId, not the signatureId. The container-lock renewal SLA is the same 5s.
        Lock certPollingLock = org.mockito.Mockito.mock(Lock.class);
        when(sessionLockRegistry.obtain(CERTIFICATE_ID)).thenReturn(certPollingLock);
        when(certPollingLock.tryLock()).thenReturn(true);
        when(containerLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(sessionService.getContainerBySessionId(SESSION_ID)).thenReturn(activeCertificateSession());
        SmartIdStatusResponse response = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.RUNNING)
                .build();
        when(smartIdApiClient.getCertificateStatus(any(), any())).thenReturn(response);

        delegate.pollSmartIdCertificateStatus(SESSION_ID, CERTIFICATE_ID, Duration.ZERO);

        verify(sessionLockRegistry, timeout(2_000)).obtain(CERTIFICATE_ID);
        verify(sessionLockRegistry, timeout(2_000)).obtain(SESSION_ID);
        verify(containerLock, timeout(2_000)).tryLock(5L, TimeUnit.SECONDS);
    }

    private static HashcodeContainerSession activeSignatureSession() {
        SignatureSession signatureSession = SignatureSession.builder()
                .signingType(SigningType.SMART_ID)
                .relyingPartyInfo(RelyingPartyInfo.builder().name("rp").uuid("uuid").build())
                .sessionCode("session-code")
                .dataFilesHash("hash1")
                .build();
        HashcodeContainerSession session = HashcodeContainerSession.builder()
                .clientName("client1").serviceName("service1")
                .serviceUuid("1c4ff3aa-afa6-11ee-8415-9790cd3b9cad")
                .sessionId(SESSION_ID)
                .build();
        session.addSignatureSession(SIGNATURE_ID, signatureSession);
        return session;
    }

    private static HashcodeContainerSession activeCertificateSession() {
        HashcodeContainerSession session = HashcodeContainerSession.builder()
                .clientName("client1").serviceName("service1")
                .serviceUuid("1c4ff3aa-afa6-11ee-8415-9790cd3b9cad")
                .sessionId(SESSION_ID)
                .build();
        session.addCertificateSession(CERTIFICATE_ID,
                ee.openeid.siga.common.session.CertificateSession.builder()
                        .relyingPartyInfo(RelyingPartyInfo.builder().name("rp").uuid("uuid").build())
                        .sessionCode("session-code")
                        .build());
        return session;
    }
}
