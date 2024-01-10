package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.ProcessingStatus;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.session.SessionService;
import org.digidoc4j.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static ee.openeid.siga.service.signature.mobileid.MobileIdSessionStatus.OUTSTANDING_TRANSACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileIdSigningDelegateTest {
    private static final String CONTAINER_ID = "container1";
    private static final String SIGNATURE_ID = "signature1";
    @Mock
    private ContainerSigningService containerSigningService;
    @Mock
    private SessionService sessionService;
    @Mock
    private Signature signature;
    @InjectMocks
    private MobileIdSigningDelegate mobileIdSigningDelegate;

    @Test
    void getMobileIdSignatureStatus_WhenContainerHasOutstandingMidSignatureSession_ReturnsOutstandingTransactionStatus() {
        SessionStatusReprocessingProperties reprocessingProperties = new SessionStatusReprocessingProperties();
        HashcodeContainerSession session = createHashcodeContainerSession();
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(reprocessingProperties);

        String signatureStatus = mobileIdSigningDelegate.getMobileIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID);

        assertEquals(OUTSTANDING_TRANSACTION.toString(), signatureStatus);
    }

    @Test
    void getMobileIdSignatureStatus_WhenSignatureIsReady_ReturnsSignatureStatus() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        SignatureSession signatureSession = session.getSignatureSession(SIGNATURE_ID);
        byte[] signature = signatureSession.getSignature();
        SessionStatus sessionStatus = signatureSession.getSessionStatus();
        sessionStatus.setStatus(SmartIdSessionStatus.OK.getSigaSigningMessage());
        sessionStatus.setProcessingStatus(ProcessingStatus.RESULT);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getSessionService()).thenReturn(sessionService);
        when(containerSigningService.finalizeSignature(session, SIGNATURE_ID, signature)).thenReturn(this.signature);

        String signatureStatus = mobileIdSigningDelegate.getMobileIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID);

        assertEquals(SmartIdSessionStatus.OK.getSigaSigningMessage(), signatureStatus);
        assertNull(session.getSignatureSession(SIGNATURE_ID));
        verify(containerSigningService).finalizeSignature(session, SIGNATURE_ID, signature);
        verify(containerSigningService).addSignatureToSession(session, this.signature, SIGNATURE_ID);
        verify(sessionService).update(session);
    }

    @Test
    void getMobileIdSignatureStatus_WhenMaxProcessingAttemptsHasBeenExceeded_ThrowsInternalServerError() {
        SessionStatusReprocessingProperties reprocessingProperties = new SessionStatusReprocessingProperties();
        HashcodeContainerSession session = createHashcodeContainerSession();
        SessionStatus sessionStatus = session.getSignatureSession(SIGNATURE_ID).getSessionStatus();
        sessionStatus.setProcessingCounter(10);
        sessionStatus.setStatusError("INTERNAL_SERVER_ERROR", "error");
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);
        when(containerSigningService.getReprocessingProperties()).thenReturn(reprocessingProperties);

        SigaApiException caughtException = assertThrows(
                SigaApiException.class, () -> mobileIdSigningDelegate.getMobileIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID)
        );

        assertEquals("INTERNAL_SERVER_ERROR", caughtException.getErrorCode());
        assertEquals("error", caughtException.getMessage());
    }

    @Test
    void getMobileIdSignatureStatus_WhenSigningTypeIsNotMobileId_ThrowsInternalServerError() {
        HashcodeContainerSession session = createHashcodeContainerSession();
        session.getSignatureSession(SIGNATURE_ID).setSigningType(SigningType.SMART_ID);
        when(containerSigningService.getSession(CONTAINER_ID)).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> mobileIdSigningDelegate.getMobileIdSignatureStatus(CONTAINER_ID, SIGNATURE_ID)
        );

        assertEquals("Unable to finalize signature for signing type: SMART_ID", caughtException.getMessage());
    }

    private static HashcodeContainerSession createHashcodeContainerSession() {
        SignatureSession signatureSession = SignatureSession.builder()
                .dataFilesHash("hash1")
                .signingType(SigningType.MOBILE_ID)
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
