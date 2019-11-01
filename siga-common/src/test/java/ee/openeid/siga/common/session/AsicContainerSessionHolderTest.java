package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.SigningType;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.IOException;

public class AsicContainerSessionHolderTest {
    private static final String DEFAULT_MOCK_CONTAINER_NAME = "container.asice";
    private static final String DEFAULT_MOCK_CLIENT_NAME = "clientName";
    private static final String DEFAULT_MOCK_SERVICE_NAME = "serviceName";
    private static final String DEFAULT_MOCK_SERVICE_UUID = "serviceUuid";
    private static final String DEFAULT_MOCK_SESSION_ID = "sessionId";
    private static final String DEFAULT_MOCK_SIGNATURE_ID = "12345_id";
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void createEmptyContainerSessionHolder() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("containerName is marked non-null but is null");
        AsicContainerSessionHolder.builder().build();
    }

    @Test
    public void createContainerSessionHolderWithOnlyContainerName() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("clientName is marked non-null but is null");
        AsicContainerSessionHolder
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutServiceName() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("serviceName is marked non-null but is null");
        AsicContainerSessionHolder
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutServiceUuid() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("serviceUuid is marked non-null but is null");
        AsicContainerSessionHolder
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutSessionId() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("sessionId is marked non-null but is null");
        AsicContainerSessionHolder
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutContainer() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("container is marked non-null but is null");
        AsicContainerSessionHolder
                .builder()
                .containerName(DEFAULT_MOCK_CONTAINER_NAME)
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .build();
    }

    @Test
    public void createValidContainerSession() throws IOException {
        AsicContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        Assert.assertEquals(DEFAULT_MOCK_CONTAINER_NAME, sessionHolder.getContainerName());
        Assert.assertEquals(DEFAULT_MOCK_CLIENT_NAME, sessionHolder.getClientName());
        Assert.assertEquals(DEFAULT_MOCK_SERVICE_NAME, sessionHolder.getServiceName());
        Assert.assertEquals(DEFAULT_MOCK_SERVICE_UUID, sessionHolder.getServiceUuid());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionHolder.getSessionId());
        Assert.assertNotNull(sessionHolder.getContainer());
    }

    @Test
    public void addSignatureToContainerSession() throws IOException {
        AsicContainerSessionHolder sessionHolder = generateDefaultSessionHolder();
        sessionHolder.addSignatureId("signatureID", 23894237);
        Integer signatureHashCode = sessionHolder.getSignatureIdHolder().get("signatureID");
        Assert.assertEquals(Integer.valueOf(23894237), signatureHashCode);
    }

    @Test
    public void addDataToSignToContainerSession() throws IOException {
        AsicContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        DataToSignHolder dataToSignHolder = DataToSignHolder.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addDataToSign(DEFAULT_MOCK_SIGNATURE_ID, dataToSignHolder);

        DataToSignHolder sessionDataToSignHolder = sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID);
        Assert.assertEquals(dataToSignHolder.getDataToSign(), sessionDataToSignHolder.getDataToSign());
        Assert.assertEquals(SigningType.REMOTE, sessionDataToSignHolder.getSigningType());
        Assert.assertNull(sessionDataToSignHolder.getSessionCode());
    }

    @Test
    public void addDataToSignAndThenRemoveItFromContainer() throws Exception {
        AsicContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        DataToSignHolder dataToSignHolder = DataToSignHolder.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addDataToSign(DEFAULT_MOCK_SIGNATURE_ID, dataToSignHolder);
        Assert.assertNotNull(sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID));
        sessionHolder.clearSigning(DEFAULT_MOCK_SIGNATURE_ID);
        Assert.assertNull(sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID));
    }

    private DataToSign generateDefaultDataToSign() {
        DataToSign dataToSignMock = Mockito.mock(DataToSign.class);
        Mockito.doReturn("hello".getBytes()).when(dataToSignMock).getDataToSign();
        return dataToSignMock;
    }

    private AsicContainerSessionHolder generateDefaultSessionHolder() throws IOException {
        Container container = ContainerBuilder
                .aContainer()
                .withDataFile(new DataFile("data".getBytes(), "datafile.txt", "application/text"))
                .build();

        return AsicContainerSessionHolder
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
