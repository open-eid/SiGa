package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.CertificateSession;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.configuration.SessionStatusReprocessingProperties;
import org.digidoc4j.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartIdSigningDelegateTest {
    private static final String CONTAINER_ID = "container1";
    private static final String SIGNATURE_ID = "signature1";
    private static final String CERTIFICATE_ID = "certificate1";
    private static final String DOCUMENT_NUMBER = "PNOEE-40504040001-DEM2-Q";
    @Mock
    private ContainerSigningService containerSigningService;
    @Mock
    private SessionService sessionService;
    @Mock
    private Signature signature;
    @Mock
    private SmartIdApiClient smartIdApiClient;
    @Mock
    private SmartIdClientConfigurationProperties configurationProperties;
    @InjectMocks
    private SmartIdSigningDelegate<ContainerSigningService> smartIdSigningDelegate;

    @Test
    void getSmartIdSignatureStatus_WhenContainerHasOutstandingSidSignatureSession_ReturnsOutstandingTransactionStatus() {
        SessionStatusReprocessingProperties reprocessingProperties = SessionStatusReprocessingProperties.withDefaults();
        HashcodeContainerSession session = createHashcodeContainerSession();
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(reprocessingProperties);

        String signatureStatus = smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID);

        assertEquals(SmartIdSessionStatus.RUNNING.getSigaSigningMessage(), signatureStatus);
    }

    @Test
    void getSmartIdSignatureStatus_WhenSignatureIsReady_ReturnsSignatureStatus() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        SignatureSession signatureSession = session.getSignatureSession(SIGNATURE_ID);
        byte[] signature = signatureSession.getSignature();
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        sessionStatus.setStatus(SmartIdSessionStatus.OK.getSigaSigningMessage());
        sessionStatus.setProcessingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);
        when(containerSigningService.finalizeSignature(session, SIGNATURE_ID, signature)).thenReturn(this.signature);

        String signatureStatus = smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID);

        assertEquals(SmartIdSessionStatus.OK.getSigaSigningMessage(), signatureStatus);
        assertNull(session.getSignatureSession(SIGNATURE_ID));
        verify(containerSigningService).finalizeSignature(session, SIGNATURE_ID, signature);
        verify(containerSigningService).addSignatureToSession(session, this.signature, SIGNATURE_ID);
        verify(sessionService).update(session);
    }

    @Test
    void getSmartIdSignatureStatus_WhenMaxProcessingAttemptsHasBeenExceeded_ThrowsInternalServerError() {
        SessionStatusReprocessingProperties reprocessingProperties = SessionStatusReprocessingProperties.withDefaults();
        HashcodeContainerSession session = createHashcodeContainerSession();
        SessionStatus sessionStatus = session.getSignatureSession(SIGNATURE_ID).getSessionStatus();
        sessionStatus.setProcessingCounter(10);
        sessionStatus.setStatusError("INTERNAL_SERVER_ERROR", "error");
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(reprocessingProperties);

        SigaApiException caughtException = assertThrows(
                SigaApiException.class, () -> smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID)
        );

        assertEquals("INTERNAL_SERVER_ERROR", caughtException.getErrorCode());
        assertEquals("error", caughtException.getMessage());
    }

    @Test
    void getSmartIdSignatureStatus_WhenSigningTypeIsNotSmartId_ThrowsInternalServerError() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        session.getSignatureSession(SIGNATURE_ID).setSigningType(SigningType.MOBILE_ID);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID)
        );

        assertEquals("Unable to finalize signature for signing type: MOBILE_ID", caughtException.getMessage());
    }

    @Test
    void getSmartIdSignatureStatus_WhenSignatureIdIsUnknown_ThrowsInvalidSessionData() {
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(createHashcodeContainerSession());

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, "unknown")
        );

        assertEquals("Unable to finalize signature. No data to sign with signature Id: unknown", caughtException.getMessage());
    }

    @Test
    void getSmartIdSignatureStatus_WhenUserRefused_ReturnsStatusWithoutFinalizingAndRemovesSigningSession() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        SessionStatus sessionStatus = session.getSignatureSession(SIGNATURE_ID).getSessionStatus();
        sessionStatus.setStatus(SmartIdSessionStatus.USER_REFUSED.getSigaSigningMessage());
        sessionStatus.setProcessingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);

        String signatureStatus = smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID);

        assertEquals(SmartIdSessionStatus.USER_REFUSED.getSigaSigningMessage(), signatureStatus);
        assertNull(session.getSignatureSession(SIGNATURE_ID));
        verify(containerSigningService, never()).finalizeSignature(any(), any(), any());
        verify(sessionService).update(session);
    }

    @Test
    void getSmartIdSignatureStatus_WhenFinalizationFails_StillRemovesSigningSession() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        SessionStatus sessionStatus = session.getSignatureSession(SIGNATURE_ID).getSessionStatus();
        sessionStatus.setStatus(SmartIdSessionStatus.OK.getSigaSigningMessage());
        sessionStatus.setProcessingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);
        when(containerSigningService.finalizeSignature(any(), any(), any())).thenThrow(new IllegalStateException("finalize failed"));

        assertThrows(IllegalStateException.class, () -> smartIdSigningDelegate.getSmartIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID));

        assertNull(session.getSignatureSession(SIGNATURE_ID));
        verify(sessionService).update(session);
    }

    @Test
    void getSmartIdCertificateStatus_WhenContainerSessionIsMissing_ThrowsInvalidSessionData() {
        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID)
        );

        assertEquals("No session found for certificate Id: " + CERTIFICATE_ID, caughtException.getMessage());
    }

    @Test
    void getSmartIdCertificateStatus_WhenCertificateIdIsUnknown_ThrowsInvalidSessionData() {
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(createHashcodeContainerSession());

        assertThrows(InvalidSessionDataException.class,
                () -> smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID));
    }

    @Test
    void getSmartIdCertificateStatus_WhenStillPolling_ReturnsOutstandingTransaction() {
        HashcodeContainerSession session = createSessionWithCertificateSession();
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(SessionStatusReprocessingProperties.withDefaults());

        CertificateStatus status = smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID);

        assertEquals(SmartIdSessionStatus.RUNNING.getSigaCertificateMessage(), status.getStatus());
        assertNull(status.getDocumentNumber());
        assertNotNull(session.getCertificateSession(CERTIFICATE_ID));
    }

    @Test
    void getSmartIdCertificateStatus_WhenCertificateIsReady_ReturnsDocumentNumberAndRemovesCertificateSession() {
        HashcodeContainerSession session = createSessionWithCertificateSession();
        CertificateSession certificateSession = session.getCertificateSession(CERTIFICATE_ID);
        certificateSession.setDocumentNumber(DOCUMENT_NUMBER);
        certificateSession.getSessionStatus().setStatus(SmartIdSessionStatus.OK.getSigaCertificateMessage());
        certificateSession.setPollingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);

        CertificateStatus status = smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID);

        assertEquals(SmartIdSessionStatus.OK.getSigaCertificateMessage(), status.getStatus());
        assertEquals(DOCUMENT_NUMBER, status.getDocumentNumber());
        assertNull(session.getCertificateSession(CERTIFICATE_ID));
        verify(sessionService).update(session);
    }

    @Test
    void getSmartIdCertificateStatus_WhenUserRefused_ReturnsUserCancelAndRemovesCertificateSession() {
        HashcodeContainerSession session = createSessionWithCertificateSession();
        CertificateSession certificateSession = session.getCertificateSession(CERTIFICATE_ID);
        certificateSession.getSessionStatus().setStatus(SmartIdSessionStatus.USER_REFUSED_CERT_CHOICE.getSigaCertificateMessage());
        certificateSession.setPollingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);

        CertificateStatus status = smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID);

        assertEquals(SmartIdSessionStatus.USER_REFUSED_CERT_CHOICE.getSigaCertificateMessage(), status.getStatus());
        assertNull(status.getDocumentNumber());
        assertNull(session.getCertificateSession(CERTIFICATE_ID));
    }

    @Test
    void getSmartIdCertificateStatus_WhenMaxProcessingAttemptsHasBeenExceeded_ThrowsRecordedError() {
        HashcodeContainerSession session = createSessionWithCertificateSession();
        SessionStatus sessionStatus = session.getCertificateSession(CERTIFICATE_ID).getSessionStatus();
        sessionStatus.setProcessingCounter(10);
        sessionStatus.setStatusError("INTERNAL_SERVER_ERROR", "SID API down");
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(SessionStatusReprocessingProperties.withDefaults());

        SigaApiException caughtException = assertThrows(SigaApiException.class,
                () -> smartIdSigningDelegate.getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID));

        assertEquals("INTERNAL_SERVER_ERROR", caughtException.getErrorCode());
        assertEquals("SID API down", caughtException.getMessage());
    }

    private static HashcodeContainerSession createSessionWithCertificateSession() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        session.addCertificateSession(CERTIFICATE_ID, CertificateSession.builder()
                .relyingPartyInfo(RelyingPartyInfo.builder().name("rp").uuid("uuid").build())
                .sessionCode("session-code")
                .build());
        return session;
    }

    private static HashcodeContainerSession createHashcodeContainerSession() {
        SignatureSession signatureSession = SignatureSession.builder()
                .dataFilesHash("hash1")
                .signingType(SigningType.SMART_ID)
                .signature(new byte[]{1, 2, 3, 4})
                .build();
        HashcodeContainerSession session = HashcodeContainerSession.builder()
                .clientName("client1")
                .serviceName("service1")
                .serviceUuid("1c4ff3aa-afa6-11ee-8415-9790cd3b9cad")
                .sessionId("session1")
                .build();
        session.addSignatureSession(SIGNATURE_ID, signatureSession);
        return session;
    }
}
