package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.mobileid.MobileIdSessionStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdStatusResponse;
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
 * Pins the lock contract introduced by commit 9dfbfe6c — "Renew Redis session locks during long
 * MID/SID polls". {@link MobileIdSigningDelegate#pollMobileIdSignatureStatus} obtains a polling
 * lock keyed by {@code signatureId} (held for the whole async poll), and then the inner status
 * handler obtains a SECOND lock keyed by {@code sessionId} with a 5-second timed wait so a
 * concurrent renewal thread can't deadlock the response write. Without this test, the 5s renewal
 * timing — and the per-key separation between polling and container locks — could regress silently.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // setUp stubs the full success path; the
// early-return tests (skip API call, container-lock timeout) exercise only a subset.
class MobileIdSigningDelegateLockRenewalTest {

    private static final String CONTAINER_ID = "container1";
    private static final String SIGNATURE_ID = "signature1";
    private static final String SESSION_ID = "v1_svc_session-1";

    @Mock
    private ContainerSigningService containerSigningService;
    @Mock
    private SessionService sessionService;
    @Mock
    private SessionLockRegistry sessionLockRegistry;
    @Mock
    private MobileIdApiClient mobileIdApiClient;
    @Mock
    private SigaEventLogger sigaEventLogger;
    @Mock
    private Lock pollingLock;
    @Mock
    private Lock containerLock;

    private ThreadPoolTaskExecutor taskExecutor;
    private MobileIdSigningDelegate delegate;

    @BeforeEach
    void setUp() {
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.initialize();

        when(containerSigningService.getTaskExecutor()).thenReturn(taskExecutor);
        when(containerSigningService.getSessionLockRegistry()).thenReturn(sessionLockRegistry);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);
        when(containerSigningService.getMobileIdApiClient()).thenReturn(mobileIdApiClient);
        when(containerSigningService.getSigaEventLogger()).thenReturn(sigaEventLogger);

        when(sessionLockRegistry.obtain(SIGNATURE_ID)).thenReturn(pollingLock);
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(containerLock);

        when(sessionService.getContainerBySessionId(SESSION_ID)).thenReturn(activeSession());

        MobileIdStatusResponse response = new MobileIdStatusResponse();
        response.setStatus(MobileIdSessionStatus.OUTSTANDING_TRANSACTION);
        when(mobileIdApiClient.getSignatureStatus(any(), any())).thenReturn(response);

        delegate = new MobileIdSigningDelegate(containerSigningService);
    }

    @AfterEach
    void shutdownExecutor() {
        taskExecutor.shutdown();
    }

    @Test
    void shouldObtainPollingLockBySignatureId_AndRenewWithContainerLockUnder5sTimeout_WhenStatusPolled() throws InterruptedException {
        when(pollingLock.tryLock()).thenReturn(true);
        when(containerLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        delegate.pollMobileIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        // Polling lock is keyed by signatureId; container-renewal lock is keyed by sessionId.
        verify(sessionLockRegistry, timeout(2_000)).obtain(SIGNATURE_ID);
        verify(sessionLockRegistry, timeout(2_000)).obtain(SESSION_ID);
        // The container lock must use the 5-second timed acquisition — that's the renewal SLA.
        verify(containerLock, timeout(2_000)).tryLock(5L, TimeUnit.SECONDS);
        verify(pollingLock, timeout(2_000)).unlock();
        verify(containerLock, timeout(2_000)).unlock();
    }

    @Test
    void shouldSkipApiCall_WhenPollingLockNotAcquired() {
        when(pollingLock.tryLock()).thenReturn(false);

        delegate.pollMobileIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        // Polling lock attempted but not held → the MID API must not be called and no container
        // lock is obtained. SessionStatusReprocessingService will retry the failed poll later.
        verify(pollingLock, timeout(2_000)).tryLock();
        verify(mobileIdApiClient, never()).getSignatureStatus(any(), any());
        verify(sessionLockRegistry, never()).obtain(SESSION_ID);
        verify(pollingLock, never()).unlock();
    }

    @Test
    void shouldSkipSessionUpdate_WhenContainerLockTimedAcquisitionFails() throws InterruptedException {
        when(pollingLock.tryLock()).thenReturn(true);
        when(containerLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);

        delegate.pollMobileIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        // Container lock timed out → no session update written; reprocessor will retry.
        verify(containerLock, timeout(2_000)).tryLock(5L, TimeUnit.SECONDS);
        verify(sessionService, timeout(2_000).times(0)).update(any());
        verify(pollingLock, timeout(2_000)).unlock();
    }

    @Test
    void shouldReleasePollingLockAndRecordException_WhenMidApiCallThrows() {
        when(pollingLock.tryLock()).thenReturn(true);
        // The MID API call inside the polling Runnable throws — exercise the catch/setPollingException
        // path. The polling lock MUST still be released (try/finally semantics inside SessionLocks),
        // otherwise the signatureId would stay locked until the next reprocessing cycle.
        when(mobileIdApiClient.getSignatureStatus(any(), any()))
                .thenThrow(new RuntimeException("MID API down"));

        delegate.pollMobileIdSignatureStatus(SESSION_ID, SIGNATURE_ID, Duration.ZERO);

        // setPollingException loads the session via getContainerBySessionId and writes an EXCEPTION
        // status back through SessionService.update — verify that update was invoked with a session
        // whose signature-session reflects the EXCEPTION state.
        verify(sessionService, timeout(2_000)).update(argThat(s ->
                s.getSignatureSession(SIGNATURE_ID) != null
                        && s.getSignatureSession(SIGNATURE_ID).getSessionStatus().getProcessingStatus()
                        == ee.openeid.siga.common.session.ProcessingStatus.EXCEPTION
                        && "MID API down".equals(
                        s.getSignatureSession(SIGNATURE_ID).getSessionStatus().getStatusError().getErrorMessage())
        ));
        // Critically: the polling lock is released even though the work threw — proves the
        // try/finally in SessionLocks.tryRun unlocks on exception.
        verify(pollingLock, timeout(2_000)).unlock();
    }

    private static HashcodeContainerSession activeSession() {
        SignatureSession signatureSession = SignatureSession.builder()
                .signingType(SigningType.MOBILE_ID)
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
}
