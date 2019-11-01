package ee.openeid.siga.common.session;

import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import ee.openeid.siga.common.model.SigningType;
import org.digidoc4j.DataToSign;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.util.Collections;

public class HashcodeContainerSessionHolderTest {

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
        exceptionRule.expectMessage("clientName is marked non-null but is null");
        HashcodeContainerSessionHolder.builder().build();
    }

    @Test
    public void createContainerSessionHolderWithOnlyClientName() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("serviceName is marked non-null but is null");
        HashcodeContainerSessionHolder
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutServiceUuid() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("serviceUuid is marked non-null but is null");
        HashcodeContainerSessionHolder
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .build();
    }

    @Test
    public void createContainerSessionHolderWithoutSessionId() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("sessionId is marked non-null but is null");
        HashcodeContainerSessionHolder
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .build();
    }

    @Test
    public void createEmptyValidContainerSession() {
        HashcodeContainerSessionHolder sessionHolder = HashcodeContainerSessionHolder
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .build();

        Assert.assertEquals(DEFAULT_MOCK_CLIENT_NAME, sessionHolder.getClientName());
        Assert.assertEquals(DEFAULT_MOCK_SERVICE_NAME, sessionHolder.getServiceName());
        Assert.assertEquals(DEFAULT_MOCK_SERVICE_UUID, sessionHolder.getServiceUuid());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionHolder.getSessionId());
    }

    @Test
    public void createValidContainerSessionWithDataFile() {
        HashcodeContainerSessionHolder sessionHolder = HashcodeContainerSessionHolder
                .builder()
                .clientName(DEFAULT_MOCK_CLIENT_NAME)
                .serviceName(DEFAULT_MOCK_SERVICE_NAME)
                .serviceUuid(DEFAULT_MOCK_SERVICE_UUID)
                .sessionId(DEFAULT_MOCK_SESSION_ID)
                .dataFiles(Collections.singletonList(generateDefaultHashcodeDataFile()))
                .build();

        Assert.assertEquals(1, sessionHolder.getDataFiles().size());
        HashcodeDataFile dataFile = sessionHolder.getDataFiles().get(0);
        Assert.assertEquals("first datafile.txt", dataFile.getFileName());
        Assert.assertEquals(Integer.valueOf(6), dataFile.getFileSize());
        Assert.assertEquals("VKZIO4rKVcnfKjW69x2ZZd39YjRo2B1RIpvV630eHBs=", dataFile.getFileHashSha256());
        Assert.assertEquals("hIVQtdcSnvLY9JK3VnZkKrJ41s1fHYFqzpiNFY4ZlkVeXiPL5Nu7Kd/cVXYEBuME26QIeI2q6gI7OjLIbl9SUw==", dataFile.getFileHashSha512());
        Assert.assertEquals("mimetype", dataFile.getMimeType());
    }

    @Test
    public void createValidContainerSessionWithSignatures() {
        HashcodeContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        Assert.assertEquals(1, sessionHolder.getSignatures().size());
        HashcodeSignatureWrapper signatureWrapper = sessionHolder.getSignatures().get(0);
        Assert.assertEquals(DEFAULT_MOCK_SIGNATURE_ID, signatureWrapper.getGeneratedSignatureId());
        Assert.assertEquals(signatureWrapper.getSignature(), signatureWrapper.getSignature());
        Assert.assertEquals(1, signatureWrapper.getDataFiles().size());
        Assert.assertEquals("first datafile.txt", signatureWrapper.getDataFiles().get(0).getFileName());
        Assert.assertEquals("SHA512", signatureWrapper.getDataFiles().get(0).getHashAlgo());
    }

    @Test
    public void addDataToSignToContainerSession() throws Exception {
        HashcodeContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        DataToSignHolder dataToSignHolder = DataToSignHolder.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addDataToSign(DEFAULT_MOCK_SIGNATURE_ID, dataToSignHolder);

        DataToSignHolder sessionDataToSignHolder = sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID);
        Assert.assertEquals(dataToSignHolder.getDataToSign(), sessionDataToSignHolder.getDataToSign());
        Assert.assertEquals(SigningType.REMOTE, sessionDataToSignHolder.getSigningType());
        Assert.assertNull(sessionDataToSignHolder.getSessionCode());
    }

    @Test
    public void addDataToSignAndThenRemoveItFromContainer() throws Exception {
        HashcodeContainerSessionHolder sessionHolder = generateDefaultSessionHolder();

        DataToSignHolder dataToSignHolder = DataToSignHolder.builder().dataToSign(generateDefaultDataToSign()).signingType(SigningType.REMOTE).build();
        sessionHolder.addDataToSign(DEFAULT_MOCK_SIGNATURE_ID, dataToSignHolder);
        Assert.assertNotNull(sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID));
        sessionHolder.clearSigning(DEFAULT_MOCK_SIGNATURE_ID);
        Assert.assertNull(sessionHolder.getDataToSignHolder(DEFAULT_MOCK_SIGNATURE_ID));
    }

    private DataToSign generateDefaultDataToSign() throws Exception {
        DataToSign dataToSignMock = Mockito.mock(DataToSign.class);
        Mockito.doReturn("hello".getBytes()).when(dataToSignMock).getDataToSign();
        return dataToSignMock;
    }

    private HashcodeContainerSessionHolder generateDefaultSessionHolder() {
        return HashcodeContainerSessionHolder
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
