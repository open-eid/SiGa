package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.model.*;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.mobileid.MobileIdSessionStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdStatusResponse;
import ee.openeid.siga.service.signature.smartid.InitSmartIdSignatureResponse;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.SmartIdStatusResponse;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import ee.sk.smartid.SmartIdCertificate;
import lombok.SneakyThrows;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static java.time.Duration.ZERO;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

public abstract class ContainerSigningServiceTest {

    private static final String SIG_ID = "sig123";
    protected final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    @Mock
    protected SessionService sessionService;
    @Mock
    private MobileIdApiClient mobileIdApiClient;
    @Mock
    private SmartIdApiClient smartIdApiClient;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @Before
    public void setUpSecurityContext() {
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder().build());
        SecurityContextHolder.setContext(securityContext);
    }

    protected void assertCreateDataToSignSuccessful() {
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate())).getDataToSign();
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(getExpectedDataToSignPrefix()));
    }

    protected void invalidContainerId() {
        getSigningService().createDataToSign("random-container-id", createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate()));
    }

    protected void assertOnlyRequiredSignatureParameters() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signatureParameters.setCountry(null);
        signatureParameters.setRoles(null);
        signatureParameters.setPostalCode(null);
        signatureParameters.setStateOrProvince(null);
        signatureParameters.setCity(null);
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        Assert.assertEquals(DigestAlgorithm.SHA512, dataToSign.getDigestAlgorithm());
        Assert.assertTrue(new String(dataToSign.getDataToSign()).startsWith(getExpectedDataToSignPrefix()));
    }

    protected void assertSignAndValidateSignature() {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        signatureParameters.setSignatureDigestAlgorithm(DigestAlgorithm.SHA512);
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        Signature signature = dataToSign.finalize(signatureRaw);
        Assert.assertEquals("Tallinn", signature.getCity());
        Assert.assertEquals("34234", signature.getPostalCode());
        Assert.assertEquals("Harjumaa", signature.getStateOrProvince());
        Assert.assertEquals("Estonia", signature.getCountryName());
        Assert.assertEquals("Engineer", signature.getSignerRoles().get(0));
        Assert.assertTrue(signature.validateSignature().isValid());
    }

    protected void assertFinalizeAndValidateSignature() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        mockRemoteSessionHolder(dataToSign);
        Result result = getSigningService().finalizeSigning(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(), base64EncodedSignature);
        Assert.assertEquals(Result.OK, result);
    }

    protected void noDataToSignInSession() {
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, Base64.getDecoder().decode("kZLQdTYDtWjSbmFlM3RO+vAfygvKDKfQHQkYrDflIDj98r28vlSTMkewVDzlsuzeIY6G+Skr1jmpQmuDr7usJQ=="));
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));
        getSigningService().finalizeSigning(CONTAINER_ID, "someUnknownSignatureId", base64EncodedSignature);
    }

    protected void noDataToSignInSessionForSignatureId() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        String base64EncodedSignature = new String(Base64.getEncoder().encode(signatureRaw));

        mockRemoteSessionHolder(dataToSign);
        try {
            getSigningService().finalizeSigning(CONTAINER_ID, "someUnknownSignatureId", base64EncodedSignature);
        } catch (InvalidSessionDataException e) {
            Result result = getSigningService().finalizeSigning(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(), base64EncodedSignature);
            Assert.assertEquals(Result.OK, result);
            throw e;
        }
    }

    protected void assertSuccessfulMobileIdSigning() throws IOException {
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        InitMidSignatureResponse initMidSignatureResponse = new InitMidSignatureResponse();
        initMidSignatureResponse.setSessionCode("sessionCode");
        initMidSignatureResponse.setChallengeId("1234");
        Mockito.when(mobileIdApiClient.initMobileSigning(any(), any(), any())).thenReturn(initMidSignatureResponse);
        Mockito.when(mobileIdApiClient.getCertificate(any(), any())).thenReturn(pkcs12Esteid2018SignatureToken.getCertificate());

        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        getSigningService().startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    protected void assertSuccessfulMobileIdSignatureProcessing(ContainerSigningService containerSigningService) throws IOException, URISyntaxException {
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        Session session = getSessionHolder();
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
        Mockito.when(sessionService.getContainerBySessionId(CONTAINER_SESSION_ID)).thenReturn(session);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        DataToSign dataToSign = getSigningService().buildDataToSign(session, signatureParameters);
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        MobileIdStatusResponse response = new MobileIdStatusResponse();
        response.setSignature(signatureRaw);
        response.setStatus(MobileIdSessionStatus.SIGNATURE);
        Mockito.when(mobileIdApiClient.getSignatureStatus(any(), any())).thenReturn(response);
        RelyingPartyInfo relyingPartyInfo = MobileIdSigningDelegate.getRelyingPartyInfo();
        session.addSignatureSession(dataToSign.getSignatureParameters().getSignatureId(),
                SignatureSession.builder()
                        .relyingPartyInfo(relyingPartyInfo)
                        .dataToSign(dataToSign)
                        .signingType(SigningType.MOBILE_ID)
                        .sessionCode("2342384932")
                        .dataFilesHash(getSigningService().generateDataFilesHash(session))
                        .build());

        getSigningService().pollMobileIdSignatureStatus(session.getSessionId(), dataToSign.getSignatureParameters().getSignatureId(), ZERO);

        await().atMost(FIVE_SECONDS)
                .untilAsserted(() -> Assert.assertEquals("SIGNATURE",
                        getSigningService().getMobileIdSignatureStatus(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId())));
        Mockito.verify(sessionService, Mockito.times(2)).update(eq(session));
        Mockito.verify(containerSigningService, Mockito.times(1)).finalizeSignature(eq(session), anyString(), any());
    }

    protected void assertSuccessfulCertificateChoice() {
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        Mockito.when(smartIdApiClient.initiateCertificateChoice(any(), any())).thenReturn(SMART_ID_SESSION_ID);
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        String certificateSessionId = getSigningService().initSmartIdCertificateChoice(CONTAINER_ID, smartIdInformation);
        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        Mockito.verify(sessionService, Mockito.times(1)).update(sessionCaptor.capture());
        Session updatedSession = sessionCaptor.getValue();
        MatcherAssert.assertThat(updatedSession.getSessionId(), equalTo(CONTAINER_SESSION_ID));
        Assert.assertEquals(36, certificateSessionId.length());
    }

    @SneakyThrows
    protected void assertSuccessfulCertificateChoiceProcessing() {
        Session session = getSessionHolder();
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(session);
        Mockito.when(sessionService.getContainerBySessionId(CONTAINER_SESSION_ID)).thenReturn(session);
        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());
        smartIdCertificate.setDocumentNumber(DOCUMENT_NUMBER);
        SmartIdStatusResponse statusResponse = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.OK)
                .smartIdCertificate(smartIdCertificate)
                .build();
        Mockito.when(smartIdApiClient.getCertificateStatus(any(), any())).thenReturn(statusResponse);

        getSigningService().pollSmartIdCertificateStatus(session.getSessionId(), CERTIFICATE_ID, ZERO);

        await().atMost(FIVE_SECONDS).untilAsserted(() -> {
            CertificateStatus certificateStatus = getSigningService().getSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID);
            Assert.assertEquals(SmartIdSessionStatus.OK.getSigaCertificateMessage(), certificateStatus.getStatus());
            Assert.assertEquals(DOCUMENT_NUMBER, certificateStatus.getDocumentNumber());
        });
        Mockito.verify(sessionService, Mockito.times(2)).update(eq(session));
    }

    protected void assertSuccessfulSmartIdSigningWithoutSessionCert() {
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
        initSmartIdSignatureResponse.setSessionCode("sessionCode");
        initSmartIdSignatureResponse.setChallengeId("1234");
        Mockito.when(smartIdApiClient.initSmartIdSigning(any(), any(), any())).thenReturn(initSmartIdSignatureResponse);

        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());

        Mockito.when(smartIdApiClient.getCertificate(any(), any())).thenReturn(smartIdCertificate);
        SignatureParameters signatureParameters = createSignatureParameters(null);
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Assert.assertNotNull(signingChallenge.getChallengeId());
        Assert.assertNotNull(signingChallenge.getGeneratedSignatureId());
    }

    protected void assertSuccessfulSmartIdSigningWithSessionCert() throws IOException, URISyntaxException {
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
        initSmartIdSignatureResponse.setSessionCode("sessionCode");
        initSmartIdSignatureResponse.setChallengeId("1234");
        Mockito.when(smartIdApiClient.initSmartIdSigning(any(), any(), any())).thenReturn(initSmartIdSignatureResponse);

        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());

        Session sessionHolder = getSessionHolder();
        sessionHolder.addCertificate(DOCUMENT_NUMBER, pkcs12Esteid2018SignatureToken.getCertificate());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        SignatureParameters signatureParameters = createSignatureParameters(null);
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Assert.assertNotNull(signingChallenge.getChallengeId());
        Assert.assertNotNull(signingChallenge.getGeneratedSignatureId());
    }

    protected void assertSuccessfulSmartIdSignatureProcessing(ContainerSigningService containerSigningService) throws IOException, URISyntaxException {
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();
        Session sessionHolder = mockSmartIdSessionHolder(dataToSign);
        byte[] signature = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SmartIdStatusResponse statusResponse = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.OK)
                .signature(signature)
                .build();
        Mockito.when(smartIdApiClient.getSignatureStatus(any(), any())).thenReturn(statusResponse);
        getSigningService().pollSmartIdSignatureStatus(sessionHolder.getSessionId(), dataToSign.getSignatureParameters().getSignatureId(), ZERO);

        await().atMost(FIVE_SECONDS)
                .untilAsserted(() -> Assert.assertEquals(SmartIdSessionStatus.OK.getSigaSigningMessage(),
                        getSigningService().getSmartIdSignatureStatus(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId())));

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        Mockito.verify(sessionService, Mockito.times(3)).update(sessionCaptor.capture());
        Session updatedSession = sessionCaptor.getValue();
        MatcherAssert.assertThat(updatedSession, equalTo(sessionHolder));
        Mockito.verify(containerSigningService, Mockito.times(1)).finalizeSignature(eq(sessionHolder), anyString(), any());
    }

    protected void assertGeneratesOrderAgnosticDataFilesHash() {
        Session session1 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile1.txt", "data1")
                .addDataFile("datafile2.txt", "data2")
                .addDataFile("datafile3.txt", "data3")
                .build();

        Session session2 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile3.txt", "data3")
                .addDataFile("datafile1.txt", "data1")
                .addDataFile("datafile2.txt", "data2")
                .build();

        String hash1 = getSigningService().generateDataFilesHash(session1);
        String hash2 = getSigningService().generateDataFilesHash(session2);
        assertEquals(hash1, hash2);
    }

    protected void assertSameFileNameButDifferentDataGeneratesDifferentHash() {
        Session session1 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile1.txt", "data1")
                .build();

        Session session2 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile1.txt", "data2")
                .build();

        String hash1 = getSigningService().generateDataFilesHash(session1);
        String hash2 = getSigningService().generateDataFilesHash(session2);
        assertNotEquals(hash1, hash2);
    }

    protected void assertSameDataButDifferentFileNameGeneratesDifferentHash() {
        Session session1 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile1.txt", "data1")
                .build();

        Session session2 = getSimpleSessionHolderBuilder()
                .addDataFile("datafile2.txt", "data1")
                .build();

        String hash1 = getSigningService().generateDataFilesHash(session1);
        String hash2 = getSigningService().generateDataFilesHash(session2);
        assertNotEquals(hash1, hash2);
    }

    protected void finalizeSignatureWithContainerDataFilesChanged() {
        Session session = getSimpleSessionHolderBuilder()
                .addDataFile("datafile.txt", "data")
                .build();
        SignatureSession signatureSession = SignatureSession.builder()
                .dataFilesHash("someRandomHashFromBefore")
                .build();
        session.addSignatureSession(SIG_ID, signatureSession);
        getSigningService().finalizeSignature(session, SIG_ID, "b64".getBytes());
    }

    protected void assertFinalizeSignatureWithContainerDataFilesChangedClearsDataToSign() {
        Session session = getSimpleSessionHolderBuilder()
                .addDataFile("datafile.txt", "data")
                .build();
        SignatureSession signatureSession = SignatureSession.builder()
                .dataFilesHash("someRandomHashFromBefore")
                .build();
        session.addSignatureSession(SIG_ID, signatureSession);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        Mockito.doNothing().when(sessionService).update(sessionCaptor.capture());

        InvalidSessionDataException e = assertThrows(InvalidSessionDataException.class, () -> {
            getSigningService().finalizeSignature(session, SIG_ID, "b64".getBytes());
        });

        MatcherAssert.assertThat(e.getMessage(), equalTo("Unable to finalize signature. Container data files have been changed after signing was initiated. Repeat signing process"));
        Session updatedSession = sessionCaptor.getValue();
        Assert.assertEquals(CONTAINER_SESSION_ID, updatedSession.getSessionId());
        Assert.assertNull(updatedSession.getSignatureSession(SIG_ID));
    }

    private SigaUserDetails createDefaultUserDetails() {
        return SigaUserDetails.builder()
                .clientName("Client_name")
                .serviceName("Service_name")
                .serviceUuid("Service_uuid")
                .skRelyingPartyName("name")
                .skRelyingPartyUuid("uuid")
                .smartIdRelyingPartyName("name")
                .smartIdRelyingPartyUuid("uuid")
                .build();
    }

    protected abstract ContainerSigningService getSigningService();

    protected abstract String getExpectedDataToSignPrefix();

    protected abstract void mockRemoteSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract Session mockMobileIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract Session mockSmartIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract Session getSessionHolder() throws IOException, URISyntaxException;

    protected abstract SimpleSessionHolderBuilder getSimpleSessionHolderBuilder();

    protected interface SimpleSessionHolderBuilder {
        SimpleSessionHolderBuilder addDataFile(String fileName, String text);

        Session build();
    }

}
