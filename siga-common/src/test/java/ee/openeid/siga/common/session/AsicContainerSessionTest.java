package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.SigningType;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsicContainerSessionTest {
    private static final String DEFAULT_MOCK_CONTAINER_NAME = "container.asice";
    private static final String DEFAULT_MOCK_CLIENT_NAME = "clientName";
    private static final String DEFAULT_MOCK_SERVICE_NAME = "serviceName";
    private static final String DEFAULT_MOCK_SERVICE_UUID = "serviceUuid";
    private static final String DEFAULT_MOCK_SESSION_ID = "sessionId";
    private static final String DEFAULT_MOCK_SIGNATURE_ID = "12345_id";

    @Test
    void createEmptyContainerSessionHolder() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession.builder().build()
        );
        assertEquals("containerName is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithOnlyContainerName() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession
                    .builder()
                    .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                    .build()
        );
        assertEquals("clientName is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutServiceName() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession
                    .builder()
                    .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .build()
        );
        assertEquals("serviceName is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutServiceUuid() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession
                    .builder()
                    .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                    .build()
        );
        assertEquals("serviceUuid is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutSessionId() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession
                    .builder()
                    .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                    .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                    .build()
        );
        assertEquals("sessionId is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createContainerSessionHolderWithoutContainer() {
        NullPointerException caughtException = assertThrows(
            NullPointerException.class, () -> AsicContainerSession
                    .builder()
                    .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                    .clientName(DEFAULT_MOCK_CLIENT_NAME)
                    .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                    .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                    .sessionId(DEFAULT_MOCK_SESSION_ID)
                    .build()
        );
        assertEquals("container is marked non-null but is null", caughtException.getMessage());
    }

    @Test
    void createValidContainerSession() throws IOException {
        AsicContainerSession sessionHolder = generateDefaultSessionHolder();

        assertEquals(DEFAULT_MOCK_CONTAINER_NAME, sessionHolder.getContainerName());
        assertEquals(DEFAULT_MOCK_CLIENT_NAME, sessionHolder.getClientName());
        assertEquals(DEFAULT_MOCK_SERVICE_NAME, sessionHolder.getServiceName());
        assertEquals(DEFAULT_MOCK_SERVICE_UUID, sessionHolder.getServiceUuid());
        assertEquals(DEFAULT_MOCK_SESSION_ID, sessionHolder.getSessionId());
        assertNotNull(sessionHolder.getContainer());
    }

    @Test
    void addSignatureToContainerSession() throws IOException {
        AsicContainerSession sessionHolder = generateDefaultSessionHolder();
        sessionHolder.addSignatureId("signatureID", 23894237);
        Integer signatureHashCode = sessionHolder.getSignatureIdHolder().get("signatureID");
        assertEquals(Integer.valueOf(23894237), signatureHashCode);
    }

    @Test
    void addDataToSignToContainerSession() throws IOException {
        AsicContainerSession sessionHolder = generateDefaultSessionHolder();

        SignatureSession signatureSession = SignatureSession.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addSignatureSession(DEFAULT_MOCK_SIGNATURE_ID, signatureSession);

        SignatureSession sessionSignatureSession = sessionHolder.getSignatureSession(DEFAULT_MOCK_SIGNATURE_ID);
        assertEquals(signatureSession.getDataToSign(), sessionSignatureSession.getDataToSign());
        assertEquals(SigningType.REMOTE, sessionSignatureSession.getSigningType());
        assertNull(sessionSignatureSession.getSessionCode());
    }

    @Test
    void addDataToSignAndThenRemoveItFromContainer() throws Exception {
        AsicContainerSession sessionHolder = generateDefaultSessionHolder();

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

    private AsicContainerSession generateDefaultSessionHolder() throws IOException {
        Container container = ContainerBuilder
                .aContainer()
                .withDataFile(new DataFile("data".getBytes(), "datafile.txt", "application/text"))
                .build();

        return AsicContainerSession
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .container(IOUtils.toByteArray(container.saveAsStream()))
                .build();
    }

}
