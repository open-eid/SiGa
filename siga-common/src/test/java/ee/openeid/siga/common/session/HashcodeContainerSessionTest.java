package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import ee.openeid.siga.common.model.SigningType;
import org.digidoc4j.DataToSign;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashcodeContainerSessionTest {

    private static final String DEFAULT_MOCK_CLIENT_NAME = "clientName";
    private static final String DEFAULT_MOCK_SERVICE_NAME = "serviceName";
    private static final String DEFAULT_MOCK_SERVICE_UUID = "serviceUuid";
    private static final String DEFAULT_MOCK_SESSION_ID = "sessionId";
    private static final String DEFAULT_MOCK_SIGNATURE_ID = "12345_id";

    @Test
    void createEmptyContainerSessionHolder() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> HashcodeContainerSession.builder().build()
        );
        assertEquals("clientName is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithOnlyClientName() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> HashcodeContainerSession
                    .builder()
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .build()
        );
        assertEquals("serviceName is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutServiceUuid() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> HashcodeContainerSession
                    .builder()
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                    .build()
        );
        assertEquals("serviceUuid is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutSessionId() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> HashcodeContainerSession
                    .builder()
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                    .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                    .build()
        );
        assertEquals("sessionId is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createEmptyValidContainerSession() {
        HashcodeContainerSession sessionHolder = HashcodeContainerSession
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .build();

        assertEquals(DEFAULT_MOCK_CLIENT_NAME, sessionHolder.getClientName());
        assertEquals(DEFAULT_MOCK_SERVICE_NAME, sessionHolder.getServiceName());
        assertEquals(DEFAULT_MOCK_SERVICE_UUID, sessionHolder.getServiceUuid());
        assertEquals(DEFAULT_MOCK_SESSION_ID, sessionHolder.getSessionId());
    }

    @Test
    void createValidContainerSessionWithDataFile() {
        HashcodeContainerSession sessionHolder = HashcodeContainerSession
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .dataFiles(Collections.singletonList(generateDefaultHashcodeDataFile()))
                .build();

        assertEquals(1, sessionHolder.getDataFiles().size());
        HashcodeDataFile dataFile = sessionHolder.getDataFiles().get(0);
        assertEquals("first datafile.txt", dataFile.getFileName());
        assertEquals(Integer.valueOf(6), dataFile.getFileSize());
        assertEquals("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=", dataFile.getFileHashSha256());
        assertEquals("hIVQtdcSnvLY9JK3VnZkKrJ41s1fHYFqzpiNFY4ZlkVeXiPL5Nu7Kd/cVXYEBuME26QIeI2q6gI7OjLIbl9SUw==", dataFile.getFileHashSha512());
        assertEquals("mimetype", dataFile.getMimeType());
    }

    @Test
    void createValidContainerSessionWithSignatures() {
        HashcodeContainerSession sessionHolder = generateDefaultSessionHolder();

        assertEquals(1, sessionHolder.getSignatures().size());
        HashcodeSignatureWrapper signatureWrapper = sessionHolder.getSignatures().get(0);
        assertEquals(DEFAULT_MOCK_SIGNATURE_ID, signatureWrapper.getGeneratedSignatureId());
        assertEquals(signatureWrapper.getSignature(), signatureWrapper.getSignature());
        assertEquals(1, signatureWrapper.getDataFiles().size());
        assertEquals("first datafile.txt", signatureWrapper.getDataFiles().get(0).getFileName());
        assertEquals("SHA512", signatureWrapper.getDataFiles().get(0).getHashAlgo());
    }

    @Test
    void addDataToSignToContainerSession() {
        HashcodeContainerSession sessionHolder = generateDefaultSessionHolder();

        SignatureSession signatureSession = SignatureSession.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addSignatureSession(DEFAULT_MOCK_SIGNATURE_ID, signatureSession);

        SignatureSession sessionSignatureSession = sessionHolder.getSignatureSession(DEFAULT_MOCK_SIGNATURE_ID);
        assertEquals(signatureSession.getDataToSign(), sessionSignatureSession.getDataToSign());
        assertEquals(SigningType.REMOTE, sessionSignatureSession.getSigningType());
        assertNull(sessionSignatureSession.getSessionCode());
    }

    @Test
    void addDataToSignAndThenRemoveItFromContainer() {
        HashcodeContainerSession sessionHolder = generateDefaultSessionHolder();

        SignatureSession signatureSession = SignatureSession.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addSignatureSession(DEFAULT_MOCK_SIGNATURE_ID, signatureSession);
        assertNotNull(sessionHolder.getSignatureSession(DEFAULT_MOCK_SIGNATURE_ID));
        sessionHolder.clearSigningSession(DEFAULT_MOCK_SIGNATURE_ID);
        assertNull(sessionHolder.getSignatureSession(DEFAULT_MOCK_SIGNATURE_ID));
    }

    private DataToSign generateDefaultDataToSign() {
        DataToSign dataToSignMock = Mockito.mock(DataToSign.class);
        Mockito.doReturn("hello".getBytes()).when(dataToSignMock).getDataToSign();
        return dataToSignMock;
    }

    private HashcodeContainerSession generateDefaultSessionHolder() {
        return HashcodeContainerSession
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .signatures(Collections.singletonList(generateDefaultSignatureWrapper()))
                .dataFiles(Collections.singletonList(generateDefaultHashcodeDataFile()))
                .build();
    }

    private HashcodeSignatureWrapper generateDefaultSignatureWrapper() {
        SignatureHashcodeDataFile signatureHashcodeDataFile = new SignatureHashcodeDataFile();
        signatureHashcodeDataFile.setFileName("first datafile.txt");
        signatureHashcodeDataFile.setHashAlgo("SHA512");

        HashcodeSignatureWrapper signatureWrapper = new HashcodeSignatureWrapper();
        signatureWrapper.setGeneratedSignatureId(DEFAULT_MOCK_SIGNATURE_ID);
        signatureWrapper.setSignature("base64".getBytes());
        signatureWrapper.setDataFiles(Collections.singletonList(signatureHashcodeDataFile));
        return signatureWrapper;
    }

    private HashcodeDataFile generateDefaultHashcodeDataFile() {
        HashcodeDataFile dataFile = new HashcodeDataFile();
        dataFile.setFileName("first datafile.txt");
        dataFile.setFileSize(6);
        dataFile.setFileHashSha256("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=");
        dataFile.setFileHashSha512("hIVQtdcSnvLY9JK3VnZkKrJ41s1fHYFqzpiNFY4ZlkVeXiPL5Nu7Kd/cVXYEBuME26QIeI2q6gI7OjLIbl9SUw==");
        dataFile.setMimeType("mimetype");
        return dataFile;
    }
}
