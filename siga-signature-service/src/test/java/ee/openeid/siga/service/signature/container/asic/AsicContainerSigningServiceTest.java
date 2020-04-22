package ee.openeid.siga.service.signature.container.asic;


import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.AsicContainerSessionHolder;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.container.ContainerSigningServiceTest;
import ee.openeid.siga.service.signature.test.RequestUtil;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.DataToSign;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.createSignatureParameters;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class AsicContainerSigningServiceTest extends ContainerSigningServiceTest {
    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference";

    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @InjectMocks
    private AsicContainerSigningService signingService;

    @Mock
    private SigaEventLogger sigaEventLogger;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        Mockito.when(sigaEventLogger.logStartEvent(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.when(sigaEventLogger.logEndEventFor(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        signingService.setConfiguration(Configuration.of(Configuration.Mode.TEST));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createAsicSessionHolder());
    }

    @Test
    public void createDataToSignSuccessfulTest() {
        assertCreateDataToSignSuccessful();
    }

    @Test
    public void invalidContainerIdTest() {
        exceptionRule.expect(TechnicalException.class);
        exceptionRule.expectMessage("Unable to parse session");
        invalidContainerId();
    }

    @Test
    public void noContainerInSession() throws IOException, URISyntaxException {
        exceptionRule.expectMessage("container is marked non-null but is null");
        AsicContainerSessionHolder sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.setContainer(null);

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    @Test
    public void onlyRequiredSignatureParametersTest() {
        assertOnlyRequiredSignatureParameters();
    }

    @Test
    public void signAndValidateSignatureTest() {
        assertSignAndValidateSignature();
    }

    @Test
    public void finalizeAndValidateSignatureTest() throws IOException, URISyntaxException {
        assertFinalizeAndValidateSignature();
    }

    @Test
    public void noDataToSignInSessionTest() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId");
        noDataToSignInSession();
    }

    @Test
    public void noDataToSignInSessionForSignatureIdTest() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId");
        noDataToSignInSessionForSignatureId();
    }

    @Test
    public void successfulMobileIdSigningTest() throws IOException {
        assertSuccessfulMobileIdSigning();
    }

    @Test
    public void successfulMobileIdSignatureStatusTest() throws IOException, URISyntaxException {
        assertSuccessfulMobileIdSignatureProcessing();
    }

    @Test
    public void noSessionFoundMobileSigning() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId");
        signingService.processMobileStatus(CONTAINER_ID, "someUnknownSignatureId", RequestUtil.createMobileInformation());
    }

    @Test
    public void successfulSmartIdSignatureTest() throws IOException {
        assertSuccessfulSmartIdSigning();
    }

    @Test
    public void successfulSmartIdSignatureStatusTest() throws IOException, URISyntaxException {
        assertSuccessfulSmartIdSignatureProcessing(sessionService);
    }

    @Test
    public void generateDataFilesHash_generatesOrderAgnosticDataFilesHash() {
        assertGeneratesOrderAgnosticDataFilesHash();
    }

    @Test
    public void generateDataFilesHash_sameFileNameButDifferentDataGeneratesDifferentHash() {
        assertSameFileNameButDifferentDataGeneratesDifferentHash();
    }

    @Test
    public void generateDataFilesHash_sameDataButDifferentFileNameGeneratesDifferentHash() {
        assertSameDataButDifferentFileNameGeneratesDifferentHash();
    }

    @Test
    public void finalizeSignatureWithContainerDataFilesChangedThrows() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. " +
                "Container data files have been changed after signing was initiated. Repeat signing process");
        finalizeSignatureWithContainerDataFilesChanged();
    }

    @Test
    public void finalizeSignatureWithContainerDataFilesChangedClearsDataToSign() {
        assertFinalizeSignatureWithContainerDataFilesChangedClearsDataToSign();
    }

    @Override
    protected ContainerSigningService getSigningService() {
        return signingService;
    }

    @Override
    protected String getExpectedDataToSignPrefix() {
        return EXPECTED_DATATOSIGN_PREFIX;
    }

    @Override
    protected void setSigningServiceParameters() {
        //Do nothing
    }

    @Override
    protected void mockRemoteSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSessionHolder sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.addDataToSign(dataToSign.getSignatureParameters().getSignatureId(),
                DataToSignHolder.builder()
                        .dataToSign(dataToSign)
                        .signingType(SigningType.REMOTE)
                        .dataFilesHash(signingService.generateDataFilesHash(sessionHolder))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
    }

    @Override
    protected void mockMobileIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSessionHolder session = RequestUtil.createAsicSessionHolder();
        session.addDataToSign(dataToSign.getSignatureParameters().getSignatureId(),
                DataToSignHolder.builder()
                        .dataToSign(dataToSign)
                        .signingType(SigningType.MOBILE_ID)
                        .sessionCode("2342384932")
                        .dataFilesHash(signingService.generateDataFilesHash(session))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
    }

    @Override
    protected void mockSmartIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSessionHolder session = RequestUtil.createAsicSessionHolder();
        session.addDataToSign(dataToSign.getSignatureParameters().getSignatureId(),
                DataToSignHolder.builder()
                        .dataToSign(dataToSign)
                        .signingType(SigningType.SMART_ID)
                        .sessionCode("2342384932")
                        .dataFilesHash(signingService.generateDataFilesHash(session))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
    }

    @Override
    protected Session getSessionHolder() throws IOException, URISyntaxException {
        return RequestUtil.createAsicSessionHolder();
    }

    @Override
    protected SimpleSessionHolderBuilder getSimpleSessionHolderBuilder() {
        return new SimpleAsicContainerSessionHolderBuilder();
    }

    private static class SimpleAsicContainerSessionHolderBuilder implements SimpleSessionHolderBuilder {
        private final List<DataFile> dataFiles = new ArrayList<>();

        public SimpleAsicContainerSessionHolderBuilder addDataFile(String fileName, String text) {
            dataFiles.add(new DataFile(text.getBytes(), fileName, "application/text"));
            return this;
        }

        public AsicContainerSessionHolder build() {
            ContainerBuilder containerBuilder = ContainerBuilder.aContainer(ASICE)
                    .withConfiguration(Configuration.of(Configuration.Mode.TEST));
            for (DataFile dataFile : dataFiles) {
                containerBuilder.withDataFile(dataFile);
            }
            Container container = containerBuilder.build();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            container.save(outputStream);

            return AsicContainerSessionHolder.builder()
                    .sessionId(RequestUtil.CONTAINER_ID)
                    .clientName(RequestUtil.CLIENT_NAME)
                    .serviceName(RequestUtil.SERVICE_NAME)
                    .serviceUuid(RequestUtil.SERVICE_UUID)
                    .containerName("test.asice")
                    .container(outputStream.toByteArray())
                    .build();
        }
    }

}
