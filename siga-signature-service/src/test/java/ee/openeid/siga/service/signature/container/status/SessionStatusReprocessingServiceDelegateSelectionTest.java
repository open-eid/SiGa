package ee.openeid.siga.service.signature.container.status;

import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.session.spi.SessionLockRegistry;
import ee.openeid.siga.session.spi.SessionStatusScanner;
import ee.openeid.siga.session.spi.StatusReprocessingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.Lock;

import static ee.openeid.siga.common.model.SigningType.MOBILE_ID;
import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionStatusReprocessingServiceDelegateSelectionTest {

    private static final String SESSION_ID = "v1_svc_session-1";

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
    private Lock sessionLock;
    @Mock
    private HashcodeContainerSigningService hashcodeSigningService;
    @Mock
    private AsicContainerSigningService asicSigningService;
    @Mock
    private MobileIdSigningDelegate<HashcodeContainerSigningService> hashcodeMidDelegate;
    @Mock
    private MobileIdSigningDelegate<AsicContainerSigningService> asicMidDelegate;
    @Mock
    private SmartIdSigningDelegate<HashcodeContainerSigningService> hashcodeSidDelegate;
    @Mock
    private SmartIdSigningDelegate<AsicContainerSigningService> asicSidDelegate;

    private final StatusReprocessingFilter filter =
            StatusReprocessingFilter.from(10, Duration.ofSeconds(30), Duration.ofSeconds(30));
    private final HashcodeContainerSession session = HashcodeContainerSession.builder()
            .sessionId(SESSION_ID).clientName("c").serviceName("s").serviceUuid("u").build();

    @BeforeEach
    void setUp() {
        when(sessionLockRegistry.obtain(SESSION_ID)).thenReturn(sessionLock);
        when(sessionLock.tryLock()).thenReturn(true);
        when(sessionService.peekContainerBySessionId(SESSION_ID)).thenReturn(session);
        when(signingServiceSelector.getContainerSigningServiceFor(session)).thenReturn(hashcodeSigningService);
        when(hashcodeMidDelegate.getContainerSigningService()).thenReturn(hashcodeSigningService);
        when(asicMidDelegate.getContainerSigningService()).thenReturn(asicSigningService);
        when(hashcodeSidDelegate.getContainerSigningService()).thenReturn(hashcodeSigningService);
        when(asicSidDelegate.getContainerSigningService()).thenReturn(asicSigningService);
    }

    @Test
    void processFailedContainerSession_WhenStaleMobileIdSignature_PolledWithSelectedServiceDelegate() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));

        service(List.of(asicMidDelegate, hashcodeMidDelegate), List.of())
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeMidDelegate).pollMobileIdSignatureStatus(SESSION_ID, "mid-1", ZERO);
        verify(asicMidDelegate, never()).pollMobileIdSignatureStatus(any(), any(), any());
        verify(sessionService, never()).update(any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedContainerSession_WhenStaleSmartIdSignature_PolledWithSelectedServiceDelegate() {
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(List.of(), List.of(asicSidDelegate, hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(asicSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedContainerSession_WhenMobileIdAndSmartIdSignatures_EachPolledByOwnSigningType() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(List.of(hashcodeMidDelegate), List.of(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeMidDelegate).pollMobileIdSignatureStatus(SESSION_ID, "mid-1", ZERO);
        verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(hashcodeMidDelegate, never()).pollMobileIdSignatureStatus(any(), eq("sid-1"), any());
        verify(hashcodeSidDelegate, never()).pollSmartIdSignatureStatus(any(), eq("mid-1"), any());
    }

    @Test
    void processFailedContainerSession_WhenMobileIdProfileOff_SignatureMarkedFailedAndLockReleased() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));

        assertDoesNotThrow(() -> service(List.of(), List.of(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID));

        assertMarkedSigningNotEnabled(session.getSignatureSessions().get("mid-1").getSessionStatus(), MOBILE_ID);
        verify(sessionService).update(session);
        verify(hashcodeSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedContainerSession_WhenMobileIdProfileOffInMixedSession_SmartIdSignatureStillPolled() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(List.of(), List.of(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        assertMarkedSigningNotEnabled(session.getSignatureSessions().get("mid-1").getSessionStatus(), MOBILE_ID);
        assertThat(session.getSignatureSessions().get("sid-1").getSessionStatus().getProcessingStatus())
                .isEqualTo(ProcessingStatus.PROCESSING);
        // Failed entry is persisted before polling starts, so the stale session copy cannot overwrite a poll result.
        InOrder inOrder = inOrder(sessionService, hashcodeSidDelegate);
        inOrder.verify(sessionService).update(session);
        inOrder.verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedContainerSession_WhenNoDelegateForSelectedService_SignatureMarkedFailed() {
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(List.of(), List.of(asicSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        assertMarkedSigningNotEnabled(session.getSignatureSessions().get("sid-1").getSessionStatus(), SMART_ID);
        verify(sessionService).update(session);
        verify(asicSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedContainerSession_WhenNoSigningServiceForSessionType_SessionSkippedAndLockReleased() {
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));
        when(signingServiceSelector.getContainerSigningServiceFor(session)).thenReturn(null);

        service(List.of(), List.of(hashcodeSidDelegate)).processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedCertificateStatusRequest_WhenMultipleStaleCertificates_AllPolledWithSelectedServiceDelegate() {
        session.addCertificateSession("cert-1", staleCertificate());
        session.addCertificateSession("cert-2", staleCertificate());

        service(List.of(), List.of(asicSidDelegate, hashcodeSidDelegate))
                .processFailedCertificateStatusRequest(filter, SESSION_ID);

        verify(hashcodeSidDelegate).pollSmartIdCertificateStatus(SESSION_ID, "cert-1", ZERO);
        verify(hashcodeSidDelegate).pollSmartIdCertificateStatus(SESSION_ID, "cert-2", ZERO);
        verify(asicSidDelegate, never()).pollSmartIdCertificateStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void processFailedCertificateStatusRequest_WhenSmartIdProfileOff_MarkedFailedAndLockReleased() {
        session.addCertificateSession("cert-1", staleCertificate());

        assertDoesNotThrow(() -> service(List.of(hashcodeMidDelegate), List.of())
                .processFailedCertificateStatusRequest(filter, SESSION_ID));

        assertMarkedSigningNotEnabled(session.getCertificateSessions().get("cert-1").getSessionStatus(), SMART_ID);
        verify(sessionService).update(session);
        verify(sessionLock).unlock();
    }

    @Test
    void scheduledScans_WhenMobileIdAndSmartIdProfilesInactive_StillScanned() {
        SessionStatusReprocessingService service = service(List.of(), List.of());

        service.processFailedStatusRequests();
        service.processFailedCertificateStatusRequests();

        verify(sessionStatusScanner).scanSignatureSessions(any(), any());
        verify(sessionStatusScanner).scanCertificateSessions(any(), any());
    }

    @Test
    void contextStartup_WhenMobileIdAndSmartIdProfilesInactive_EmptyDelegateListsInjected() {
        // No delegate beans at all: Spring must inject empty lists rather than fail the context.
        new ApplicationContextRunner()
                .withBean(ThreadPoolTaskExecutor.class, () -> taskExecutor)
                .withBean(SessionStatusScanner.class, () -> sessionStatusScanner)
                .withBean(ContainerSigningServiceSelector.class, () -> signingServiceSelector)
                .withBean(SessionService.class, () -> sessionService)
                .withBean(SessionLockRegistry.class, () -> sessionLockRegistry)
                .withBean(SessionStatusReprocessingProperties.class, SessionStatusReprocessingProperties::withDefaults)
                .withBean(SessionStatusReprocessingService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(SessionStatusReprocessingService.class).processFailedContainerSession(filter, SESSION_ID);
                });
    }

    private SessionStatusReprocessingService service(List<MobileIdSigningDelegate<?>> midDelegates,
                                                     List<SmartIdSigningDelegate<?>> sidDelegates) {
        return new SessionStatusReprocessingService(taskExecutor, sessionStatusScanner, signingServiceSelector,
                sessionService, sessionLockRegistry, SessionStatusReprocessingProperties.withDefaults(),
                midDelegates, sidDelegates);
    }

    private static void assertMarkedSigningNotEnabled(SessionStatus status, SigningType signingType) {
        assertThat(status.getProcessingStatus()).isEqualTo(ProcessingStatus.EXCEPTION);
        assertThat(status.getProcessingCounter()).isEqualTo(1);
        assertThat(status.getStatusError().getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(status.getStatusError().getErrorMessage()).isEqualTo(signingType + " signing not enabled");
    }

    private static SignatureSession staleSignature(SigningType signingType) {
        return SignatureSession.builder().signingType(signingType).sessionStatus(staleStatus()).build();
    }

    private static CertificateSession staleCertificate() {
        return CertificateSession.builder().sessionStatus(staleStatus()).build();
    }

    private static SessionStatus staleStatus() {
        return SessionStatus.builder()
                .processingStatus(ProcessingStatus.PROCESSING)
                .processingStatusTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                .build();
    }
}
