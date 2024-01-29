package ee.openeid.siga.service.signature.container.asic;


import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.container.ContainerSigningService;
import ee.openeid.siga.service.signature.container.ContainerSigningServiceTest;
import ee.openeid.siga.service.signature.container.MobileIdSigningDelegate;
import ee.openeid.siga.service.signature.container.SmartIdSigningDelegate;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSemaphore;
import org.digidoc4j.*;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AsicContainerSigningServiceTest extends ContainerSigningServiceTest {
    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference";
    private static final String ASICE_CONTAINER_WITH_EMPTY_DATAFILES = "unsignedContainerWithEmptyDatafiles.asice";

    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ECC_from_TEST_of_ESTEID2018.p12", "1234".toCharArray());

    @Spy
    @InjectMocks
    private AsicContainerSigningService signingService;

    @Mock
    private SigaEventLogger sigaEventLogger;
    @Spy
    private ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    @Mock
    private Ignite ignite;
    @Mock
    private IgniteSemaphore igniteSemaphore;
    @Mock
    private MobileIdClientConfigurationProperties mobileIdConfigurationProperties;
    @Mock
    private SmartIdClientConfigurationProperties smartIdConfigurationProperties;
    @Mock
    private SessionStatusReprocessingProperties reprocessingProperties;
    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        Mockito.lenient().when(sigaEventLogger.logStartEvent(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.lenient().when(sigaEventLogger.logEndEventFor(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        MobileIdSigningDelegate mobileIdSigningDelegate = new MobileIdSigningDelegate((signingService));
        Mockito.lenient().when(signingService.getMobileIdSigningDelegate()).thenReturn(mobileIdSigningDelegate);
        SmartIdSigningDelegate smartIdSigningDelegate = new SmartIdSigningDelegate((signingService));
        Mockito.lenient().when(signingService.getSmartIdSigningDelegate()).thenReturn(smartIdSigningDelegate);
        Mockito.lenient().when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createAsicSessionHolder());
        taskExecutor.initialize();
        Mockito.lenient().when(mobileIdConfigurationProperties.getStatusPollingDelay()).thenReturn(Duration.ofSeconds(0));
        Mockito.lenient().when(smartIdConfigurationProperties.getStatusPollingDelay()).thenReturn(Duration.ofSeconds(0));
        Mockito.lenient().when(igniteSemaphore.tryAcquire()).thenReturn(true);
        Mockito.lenient().when(igniteSemaphore.tryAcquire(anyLong(), any())).thenReturn(true);
        Mockito.lenient().when(ignite.semaphore(anyString(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(igniteSemaphore);
    }

    @Test
    void createDataToSignSuccessfulTest() {
        assertCreateDataToSignSuccessful();
    }

    @Test
    void invalidContainerIdTest() {
        TechnicalException caughtException = assertThrows(
            TechnicalException.class, this::invalidContainerId
        );
        assertEquals("Unable to parse session object", caughtException.getMessage());
    }

    @Test
    public void noContainerInSession() {
        AsicContainerSession sessionHolder = Mockito.mock(AsicContainerSession.class);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        Mockito.when(sessionHolder.getContainer()).thenReturn(null);
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class,
                () -> signingService.createDataToSign(CONTAINER_ID, signatureParameters)
        );

        assertEquals("Unable to create signature. Container must exist", caughtException.getMessage());
    }

    @Test
    void containerWithEmptyDataFilesInSession() throws IOException, URISyntaxException {
        AsicContainerSession sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.setContainer(TestUtil.getFile(ASICE_CONTAINER_WITH_EMPTY_DATAFILES));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()))
        );
        assertEquals("Unable to sign container with empty datafiles", caughtException.getMessage());
    }

    @Test
    void onlyRequiredSignatureParametersTest() {
        assertOnlyRequiredSignatureParameters();
    }

    @Test
    void signAndValidateSignatureTest() {
        assertSignAndValidateSignature();
    }

    @Test
    void finalizeAndValidateSignatureTest() throws IOException, URISyntaxException {
        assertFinalizeAndValidateSignature();
    }

    @Test
    void noDataToSignInSessionTest() {
        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, this::noDataToSignInSession
        );
        assertEquals("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId", caughtException.getMessage());
    }

    @Test
    void noDataToSignInSessionForSignatureIdTest() {
        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, this::noDataToSignInSessionForSignatureId
        );
        assertEquals("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId", caughtException.getMessage());
    }

    @Test
    void successfulMobileIdSigningTest() {
        assertSuccessfulMobileIdSigning();
    }

    @Test
    void successfulMobileIdSignatureStatusTest() throws IOException, URISyntaxException {
        assertSuccessfulMobileIdSignatureProcessing(signingService);
    }

    @Test
    void noSessionFoundMobileSigning() {
        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.getMobileIdSignatureStatus(CONTAINER_ID, "someUnknownSignatureId")
        );
        assertEquals("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId", caughtException.getMessage());
    }

    @Test
    void containerWithEmptyDataFilesInSessionStartMobileSigning() throws IOException, URISyntaxException {
        AsicContainerSession sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.setContainer(TestUtil.getFile(ASICE_CONTAINER_WITH_EMPTY_DATAFILES));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.startMobileIdSigning(CONTAINER_ID, Mockito.mock(MobileIdInformation.class),
                    createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()))
        );
        assertEquals("Unable to sign container with empty datafiles", caughtException.getMessage());
    }

    @Test
    void successfulCertificateChoice(){
        assertSuccessfulCertificateChoice();
    }

    @Test
    void successfulCertificateChoiceProcessing(){
        assertSuccessfulCertificateChoiceProcessing();
    }

    @Test
    void successfulSmartIdSigningWithSessionCert() throws IOException, URISyntaxException {
        assertSuccessfulSmartIdSigningWithSessionCert();
    }

    @Test
    void successfulSmartIdSigningWithoutSessionCert() {
        assertSuccessfulSmartIdSigningWithoutSessionCert();
    }

    @Test
    void successfulSmartIdSignatureStatusTest() throws IOException, URISyntaxException {
        assertSuccessfulSmartIdSignatureProcessing(signingService);
    }

    @Test
    void containerWithEmptyDataFilesInSessionInitSmartIdSigning() throws IOException, URISyntaxException {
        AsicContainerSession sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.setContainer(TestUtil.getFile(ASICE_CONTAINER_WITH_EMPTY_DATAFILES));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.initSmartIdCertificateChoice(CONTAINER_ID, Mockito.mock(SmartIdInformation.class))
        );
        assertEquals("Unable to sign container with empty datafiles", caughtException.getMessage());
    }

    @Test
    void containerWithEmptyDataFilesInSessionStartSmartIdSigning() throws IOException, URISyntaxException {
        AsicContainerSession sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.setContainer(TestUtil.getFile(ASICE_CONTAINER_WITH_EMPTY_DATAFILES));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.startSmartIdSigning(CONTAINER_ID, Mockito.mock(SmartIdInformation.class),
                    createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()))
        );
        assertEquals("Unable to sign container with empty datafiles", caughtException.getMessage());
    }

    @Test
    void generateDataFilesHash_generatesOrderAgnosticDataFilesHash() {
        assertGeneratesOrderAgnosticDataFilesHash();
    }

    @Test
    void generateDataFilesHash_sameFileNameButDifferentDataGeneratesDifferentHash() {
        assertSameFileNameButDifferentDataGeneratesDifferentHash();
    }

    @Test
    void generateDataFilesHash_sameDataButDifferentFileNameGeneratesDifferentHash() {
        assertSameDataButDifferentFileNameGeneratesDifferentHash();
    }

    @Test
    void finalizeSignatureWithContainerDataFilesChangedThrows() {
        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, this::finalizeSignatureWithContainerDataFilesChanged
        );
        assertEquals("Unable to finalize signature. " +
                "Container data files have been changed after signing was initiated. Repeat signing process", caughtException.getMessage());
    }

    @Test
    void finalizeSignatureWithContainerDataFilesChangedClearsDataToSign() {
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
    protected void mockRemoteSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSession sessionHolder = RequestUtil.createAsicSessionHolder();
        sessionHolder.addSignatureSession(dataToSign.getSignatureParameters().getSignatureId(),
                SignatureSession.builder()
                        .dataToSign(dataToSign)
                        .signingType(SigningType.REMOTE)
                        .dataFilesHash(signingService.generateDataFilesHash(sessionHolder))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
    }

    @Override
    protected Session mockMobileIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSession session = RequestUtil.createAsicSessionHolder();
        session.addSignatureSession(dataToSign.getSignatureParameters().getSignatureId(),
                SignatureSession.builder()
                        .relyingPartyInfo(MobileIdSigningDelegate.getRelyingPartyInfo())
                        .dataToSign(dataToSign)
                        .signingType(SigningType.MOBILE_ID)
                        .sessionCode("2342384932")
                        .dataFilesHash(signingService.generateDataFilesHash(session))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
        Mockito.when(sessionService.getContainerBySessionId(CONTAINER_SESSION_ID)).thenReturn(session);
        return session;
    }

    @Override
    protected Session mockSmartIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException {
        AsicContainerSession session = RequestUtil.createAsicSessionHolder();
        session.addSignatureSession(dataToSign.getSignatureParameters().getSignatureId(),
                SignatureSession.builder()
                        .relyingPartyInfo(SmartIdSigningDelegate.getRelyingPartyInfo())
                        .dataToSign(dataToSign)
                        .signingType(SigningType.SMART_ID)
                        .sessionCode("2342384932")
                        .dataFilesHash(signingService.generateDataFilesHash(session))
                        .build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
        Mockito.when(sessionService.getContainerBySessionId(CONTAINER_SESSION_ID)).thenReturn(session);
        return session;
    }

    @Override
    protected Session getSessionHolder() throws IOException, URISyntaxException {
        return RequestUtil.createAsicSessionHolder();
    }

    @Override
    protected SimpleSessionHolderBuilder getSimpleSessionHolderBuilder() {
        return new SimpleAsicContainerSessionBuilder();
    }

    private static class SimpleAsicContainerSessionBuilder implements SimpleSessionHolderBuilder {
        private final List<DataFile> dataFiles = new ArrayList<>();

        public SimpleAsicContainerSessionBuilder addDataFile(String fileName, String text) {
            dataFiles.add(new DataFile(text.getBytes(), fileName, "application/text"));
            return this;
        }

        public AsicContainerSession build() {
            ContainerBuilder containerBuilder = ContainerBuilder.aContainer(ASICE)
                    .withConfiguration(Configuration.of(Configuration.Mode.TEST));
            for (DataFile dataFile : dataFiles) {
                containerBuilder.withDataFile(dataFile);
            }
            Container container = containerBuilder.build();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            container.save(outputStream);

            return AsicContainerSession.builder()
                    .sessionId(CONTAINER_SESSION_ID)
                    .clientName(CLIENT_NAME)
                    .serviceName(SERVICE_NAME)
                    .serviceUuid(SERVICE_UUID)
                    .containerName("test.asice")
                    .container(outputStream.toByteArray())
                    .build();
        }
    }

}
