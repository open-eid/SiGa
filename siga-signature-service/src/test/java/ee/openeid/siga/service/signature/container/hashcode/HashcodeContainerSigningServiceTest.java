package ee.openeid.siga.service.signature.container.hashcode;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SigningType;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.session.HashcodeContainerSession;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteSemaphore;
import org.digidoc4j.Configuration;
import org.digidoc4j.DataToSign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class HashcodeContainerSigningServiceTest extends ContainerSigningServiceTest {
    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference";

    @Spy
    @InjectMocks
    private HashcodeContainerSigningService signingService;

    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);;
    @Mock
    private SigaEventLogger sigaEventLogger;
    @Mock
    private Authentication authentication;
    @Mock
    private SecurityContext securityContext;
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

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        configuration = new Configuration(Configuration.Mode.TEST);
        MobileIdSigningDelegate mobileIdSigningDelegate = new MobileIdSigningDelegate((signingService));
        Mockito.lenient().when(signingService.getMobileIdSigningDelegate()).thenReturn(mobileIdSigningDelegate);
        SmartIdSigningDelegate smartIdSigningDelegate = new SmartIdSigningDelegate((signingService));
        Mockito.lenient().when(signingService.getSmartIdSigningDelegate()).thenReturn(smartIdSigningDelegate);
        Mockito.lenient().when(sigaEventLogger.logStartEvent(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.lenient().when(sigaEventLogger.logEndEventFor(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.lenient().when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createHashcodeSessionHolder());
        taskExecutor.initialize();
        Mockito.lenient().when(igniteSemaphore.tryAcquire()).thenReturn(true);
        Mockito.lenient().when(igniteSemaphore.tryAcquire(anyLong(), any())).thenReturn(true);
        Mockito.lenient().when(ignite.semaphore(anyString(),anyInt(),anyBoolean(), anyBoolean())).thenReturn(igniteSemaphore);
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
    void noDataFilesInSession() throws IOException, URISyntaxException {
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().clear();
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()))
        );
        assertEquals("Unable to create signature. Data files must be added to container", caughtException.getMessage());
    }

    @Test
    void emptyDataFilesInSession() throws IOException, URISyntaxException {
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));
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
    void emptyDataFilesInSessionStartMobileSigning() throws IOException, URISyntaxException {
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));
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
    void emptyDataFilesInSessionInitSmartIdSigning() throws IOException, URISyntaxException {
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> signingService.initSmartIdCertificateChoice(CONTAINER_ID, Mockito.mock(SmartIdInformation.class))
        );
        assertEquals("Unable to sign container with empty datafiles", caughtException.getMessage());
    }

    @Test
    void emptyDataFilesInSessionStartSmartIdSigning() throws IOException, URISyntaxException {
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));
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
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
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
        HashcodeContainerSession session = RequestUtil.createHashcodeSessionHolder();
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
        HashcodeContainerSession session = RequestUtil.createHashcodeSessionHolder();
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
        return RequestUtil.createHashcodeSessionHolder();
    }

    @Override
    protected SimpleSessionHolderBuilder getSimpleSessionHolderBuilder() {
        return new SimpleHashcodeContainerSessionBuilder();
    }

    private static class SimpleHashcodeContainerSessionBuilder implements SimpleSessionHolderBuilder{
        private final List<HashcodeDataFile> dataFiles = new ArrayList<>();

        public SimpleHashcodeContainerSessionBuilder addDataFile(String fileName, String text) {
            HashcodeDataFile dataFile = new HashcodeDataFile();
            dataFile.setFileName(fileName);
            dataFile.setFileHashSha256(new String(DigestUtils.sha256(text)));
            dataFiles.add(dataFile);
            return this;
        }

        public HashcodeContainerSession build() {
            return HashcodeContainerSession.builder()
                    .sessionId(CONTAINER_SESSION_ID)
                    .clientName(CLIENT_NAME)
                    .serviceName(SERVICE_NAME)
                    .serviceUuid(SERVICE_UUID)
                    .dataFiles(dataFiles)
                    .build();
        }
    }

}
