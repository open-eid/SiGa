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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;

import static ee.openeid.siga.common.model.SigningType.MOBILE_ID;
import static ee.openeid.siga.common.model.SigningType.SMART_ID;
import static java.time.Duration.ZERO;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void shouldPollMobileIdSignature_WithDelegateOfSelectedSigningService() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));

        service(providerOf(asicMidDelegate, hashcodeMidDelegate), providerOf())
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeMidDelegate).pollMobileIdSignatureStatus(SESSION_ID, "mid-1", ZERO);
        verify(asicMidDelegate, never()).pollMobileIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldPollSmartIdSignature_WithDelegateOfSelectedSigningService() {
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(providerOf(), providerOf(asicSidDelegate, hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(asicSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldRouteEachSignatureToItsOwnSigningType_WhenSessionHasMobileIdAndSmartIdSignatures() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(providerOf(hashcodeMidDelegate), providerOf(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeMidDelegate).pollMobileIdSignatureStatus(SESSION_ID, "mid-1", ZERO);
        verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(hashcodeMidDelegate, never()).pollMobileIdSignatureStatus(any(), eq("sid-1"), any());
        verify(hashcodeSidDelegate, never()).pollSmartIdSignatureStatus(any(), eq("mid-1"), any());
    }

    @Test
    void shouldSkipMobileIdSignatureAndReleaseLock_WhenMobileIdProfileIsOff() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));

        assertDoesNotThrow(() -> service(providerOf(), providerOf(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID));

        verify(hashcodeSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldStillPollSmartIdSignature_WhenMobileIdProfileIsOffForSameSession() {
        session.addSignatureSession("mid-1", staleSignature(MOBILE_ID));
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(providerOf(), providerOf(hashcodeSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(hashcodeSidDelegate).pollSmartIdSignatureStatus(SESSION_ID, "sid-1", ZERO);
        verify(sessionLock).unlock();
    }

    @Test
    void shouldSkipSmartIdSignature_WhenNoDelegateBelongsToSelectedSigningService() {
        session.addSignatureSession("sid-1", staleSignature(SMART_ID));

        service(providerOf(), providerOf(asicSidDelegate))
                .processFailedContainerSession(filter, SESSION_ID);

        verify(asicSidDelegate, never()).pollSmartIdSignatureStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldPollEveryDueCertificateSession_WithDelegateOfSelectedSigningService() {
        session.addCertificateSession("cert-1", staleCertificate());
        session.addCertificateSession("cert-2", staleCertificate());

        service(providerOf(), providerOf(asicSidDelegate, hashcodeSidDelegate))
                .processFailedCertificateStatusRequest(filter, SESSION_ID);

        verify(hashcodeSidDelegate).pollSmartIdCertificateStatus(SESSION_ID, "cert-1", ZERO);
        verify(hashcodeSidDelegate).pollSmartIdCertificateStatus(SESSION_ID, "cert-2", ZERO);
        verify(asicSidDelegate, never()).pollSmartIdCertificateStatus(any(), any(), any());
        verify(sessionLock).unlock();
    }

    @Test
    void shouldSkipCertificateReprocessingAndReleaseLock_WhenSmartIdProfileIsOff() {
        session.addCertificateSession("cert-1", staleCertificate());

        assertDoesNotThrow(() -> service(providerOf(hashcodeMidDelegate), providerOf())
                .processFailedCertificateStatusRequest(filter, SESSION_ID));

        verify(sessionLock).unlock();
    }

    private SessionStatusReprocessingService service(ObjectProvider<MobileIdSigningDelegate<?>> midDelegates,
                                                     ObjectProvider<SmartIdSigningDelegate<?>> sidDelegates) {
        return new SessionStatusReprocessingService(taskExecutor, sessionStatusScanner, signingServiceSelector,
                sessionService, sessionLockRegistry, SessionStatusReprocessingProperties.withDefaults(),
                midDelegates, sidDelegates);
    }

    // thenAnswer: a Stream can only be consumed once, and the service looks delegates up per signature session.
    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T... beans) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(invocation -> Arrays.stream(beans));
        return provider;
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
