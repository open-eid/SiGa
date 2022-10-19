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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(MockitoJUnitRunner.class)
public class HashcodeContainerSigningServiceTest extends ContainerSigningServiceTest {
    private static final String EXPECTED_DATATOSIGN_PREFIX = "<ds:SignedInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\"><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"></ds:CanonicalizationMethod><ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512\"></ds:SignatureMethod><ds:Reference";
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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

    @Before
    public void setUp() throws IOException, URISyntaxException {
        configuration = new Configuration(Configuration.Mode.TEST);
        MobileIdSigningDelegate mobileIdSigningDelegate = new MobileIdSigningDelegate((signingService));
        Mockito.when(signingService.getMobileIdSigningDelegate()).thenReturn(mobileIdSigningDelegate);
        SmartIdSigningDelegate smartIdSigningDelegate = new SmartIdSigningDelegate((signingService));
        Mockito.when(signingService.getSmartIdSigningDelegate()).thenReturn(smartIdSigningDelegate);
        Mockito.when(sigaEventLogger.logStartEvent(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.when(sigaEventLogger.logEndEventFor(any())).thenReturn(SigaEvent.builder().timestamp(0L).build());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(RequestUtil.createHashcodeSessionHolder());
        taskExecutor.initialize();
        Mockito.when(igniteSemaphore.tryAcquire()).thenReturn(true);
        Mockito.when(igniteSemaphore.tryAcquire(anyLong(), any())).thenReturn(true);
        Mockito.when(ignite.semaphore(anyString(),anyInt(),anyBoolean(), anyBoolean())).thenReturn(igniteSemaphore);
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
    public void noDataFilesInSession() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to create signature. Data files must be added to container");
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().clear();

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    @Test
    public void emptyDataFilesInSession() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to sign container with empty datafiles");
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));

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
        assertSuccessfulMobileIdSignatureProcessing(signingService);
    }

    @Test
    public void noSessionFoundMobileSigning() {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to finalize signature. No data to sign with signature Id: someUnknownSignatureId");
        signingService.getMobileIdSignatureStatus(CONTAINER_ID, "someUnknownSignatureId");
    }

    @Test
    public void emptyDataFilesInSessionStartMobileSigning() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to sign container with empty datafiles");
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.startMobileIdSigning(CONTAINER_ID, Mockito.mock(MobileIdInformation.class),
                createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    @Test
    public void successfulCertificateChoice(){
        assertSuccessfulCertificateChoice();
    }

    @Test
    public void successfulCertificateChoiceProcessing(){
        assertSuccessfulCertificateChoiceProcessing();
    }

    @Test
    public void successfulSmartIdSigningWithSessionCert() throws IOException, URISyntaxException {
        assertSuccessfulSmartIdSigningWithSessionCert();
    }

    @Test
    public void successfulSmartIdSigningWithoutSessionCert() throws IOException {
        assertSuccessfulSmartIdSigningWithoutSessionCert();
    }

    @Test
    public void successfulSmartIdSignatureStatusTest() throws IOException, URISyntaxException {
        assertSuccessfulSmartIdSignatureProcessing(signingService);
    }

    @Test
    public void emptyDataFilesInSessionInitSmartIdSigning() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to sign container with empty datafiles");
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.initSmartIdCertificateChoice(CONTAINER_ID, Mockito.mock(SmartIdInformation.class));
    }

    @Test
    public void emptyDataFilesInSessionStartSmartIdSigning() throws IOException, URISyntaxException {
        exceptionRule.expect(InvalidSessionDataException.class);
        exceptionRule.expectMessage("Unable to sign container with empty datafiles");
        HashcodeContainerSession sessionHolder = RequestUtil.createHashcodeSessionHolder();
        sessionHolder.getDataFiles().add(RequestUtil.createHashcodeDataFileFrom("empty.file", "application/octet-stream"));

        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);
        signingService.startSmartIdSigning(CONTAINER_ID, Mockito.mock(SmartIdInformation.class),
                createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
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
