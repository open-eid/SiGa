package ee.openeid.siga.service.signature.container;

import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.DataToSignHolder;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.mobileid.GetStatusResponse;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MidStatus;
import ee.openeid.siga.service.signature.mobileid.MobileIdClient;
import ee.openeid.siga.service.signature.smartid.InitSmartIdSignatureResponse;
import ee.openeid.siga.service.signature.smartid.SigaSmartIdClient;
import ee.openeid.siga.service.signature.smartid.SmartIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.SmartIdStatusResponse;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import ee.sk.smartid.SmartIdCertificate;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public abstract class ContainerSigningServiceTest {

    private static final String SIG_ID = "sig123";

    @Mock
    private MobileIdClient mobileIdClient;

    @Mock
    private SigaSmartIdClient smartIdClient;

    @Mock
    protected SessionService sessionService;

    protected final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

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
        setSigningServiceParameters();
        signatureParameters.setDigestAlgorithm(DigestAlgorithm.SHA512);
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
        setSigningServiceParameters();
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
        setSigningServiceParameters();
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
        InitMidSignatureResponse initMidSignatureResponse = new InitMidSignatureResponse();
        initMidSignatureResponse.setSessionCode("sessionCode");
        initMidSignatureResponse.setChallengeId("1234");
        Mockito.when(mobileIdClient.initMobileSigning(any(), any())).thenReturn(initMidSignatureResponse);
        Mockito.when(mobileIdClient.getCertificate(any())).thenReturn(pkcs12Esteid2018SignatureToken.getCertificate());

        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        getSigningService().startMobileIdSigning(CONTAINER_ID, mobileIdInformation, signatureParameters);
    }

    protected void assertSuccessfulMobileIdSignatureProcessing() throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();

        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());

        GetStatusResponse response = new GetStatusResponse();
        response.setSignature(signatureRaw);
        response.setStatus(MidStatus.SIGNATURE);

        Mockito.when(mobileIdClient.getStatus(any(), any())).thenReturn(response);

        mockMobileIdSessionHolder(dataToSign);
        String status = getSigningService().processMobileStatus(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(), RequestUtil.createMobileInformation());
        Assert.assertEquals("SIGNATURE", status);
    }

    protected void assertSuccessfulCertificateChoice() {
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        Mockito.when(smartIdClient.initiateCertificateChoice(smartIdInformation)).thenReturn(SMART_ID_SESSION_ID);
        String sessionId = getSigningService().initSmartIdCertificateChoice(CONTAINER_ID, smartIdInformation);
        Mockito.verify(sessionService, Mockito.times(1)).update(eq(CONTAINER_ID), any());
        Assert.assertEquals(36, sessionId.length());
    }

    protected void assertSuccessfulCertificateChoiceProcessing(){
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();

        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());
        smartIdCertificate.setDocumentNumber(DOCUMENT_NUMBER);

        SmartIdStatusResponse statusResponse = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.OK)
                .smartIdCertificate(smartIdCertificate)
                .build();
        Mockito.when(smartIdClient.getSmartIdStatus(any(), any())).thenReturn(statusResponse);
        CertificateStatus status = getSigningService().processSmartIdCertificateStatus(CONTAINER_ID, CERTIFICATE_ID, smartIdInformation);
        Assert.assertEquals("CERTIFICATE", status.getStatus());
        Assert.assertEquals(DOCUMENT_NUMBER, status.getDocumentNumber());
        Mockito.verify(sessionService, Mockito.times(1)).update(eq(CONTAINER_ID), any());
    }

    protected void assertSuccessfulSmartIdSigningWithoutSessionCert() {
        InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
        initSmartIdSignatureResponse.setSessionCode("sessionCode");
        initSmartIdSignatureResponse.setChallengeId("1234");
        Mockito.when(smartIdClient.initSmartIdSigning(any(), any())).thenReturn(initSmartIdSignatureResponse);

        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());

        Mockito.when(smartIdClient.getCertificate(any())).thenReturn(smartIdCertificate);
        SignatureParameters signatureParameters = createSignatureParameters(null);
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Assert.assertNotNull(signingChallenge.getChallengeId());
        Assert.assertNotNull(signingChallenge.getGeneratedSignatureId());
    }

    protected void assertSuccessfulSmartIdSigningWithSessionCert() throws IOException, URISyntaxException {
        InitSmartIdSignatureResponse initSmartIdSignatureResponse = new InitSmartIdSignatureResponse();
        initSmartIdSignatureResponse.setSessionCode("sessionCode");
        initSmartIdSignatureResponse.setChallengeId("1234");
        Mockito.when(smartIdClient.initSmartIdSigning(any(), any())).thenReturn(initSmartIdSignatureResponse);

        SmartIdCertificate smartIdCertificate = new SmartIdCertificate();
        smartIdCertificate.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());

        Session sessionHolder = getSessionHolder();
        sessionHolder.addCertificate(DOCUMENT_NUMBER, pkcs12Esteid2018SignatureToken.getCertificate());
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        SignatureParameters signatureParameters = createSignatureParameters(null);
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        smartIdInformation.setDocumentNumber(DOCUMENT_NUMBER);
        SigningChallenge signingChallenge = getSigningService().startSmartIdSigning(CONTAINER_ID, smartIdInformation, signatureParameters);
        Assert.assertNotNull(signingChallenge.getChallengeId());
        Assert.assertNotNull(signingChallenge.getGeneratedSignatureId());
    }

    protected void assertSuccessfulSmartIdSignatureProcessing(SessionService sessionService,
                                                              ContainerSigningService containerSigningService) throws IOException, URISyntaxException {
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        setSigningServiceParameters();
        DataToSign dataToSign = getSigningService().createDataToSign(CONTAINER_ID, signatureParameters).getDataToSign();

        Session sessionHolder = getSessionHolder();
        Mockito.when(sessionService.getContainer(CONTAINER_ID)).thenReturn(sessionHolder);

        byte[] signature = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, dataToSign.getDataToSign());
        SmartIdStatusResponse statusResponse = SmartIdStatusResponse.builder()
                .status(SmartIdSessionStatus.OK)
                .signature(signature)
                .build();

        Mockito.when(smartIdClient.getSmartIdStatus(any(), any())).thenReturn(statusResponse);

        mockSmartIdSessionHolder(dataToSign);
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        String status = getSigningService().processSmartIdStatus(CONTAINER_ID, dataToSign.getSignatureParameters().getSignatureId(),
                smartIdInformation);
        Assert.assertEquals(SmartIdSessionStatus.OK.getSigaMessage(SmartIdSessionStatus.SessionType.SIGN), status);
        Mockito.verify(sessionService, Mockito.times(2)).update(eq(CONTAINER_ID), any());
        Mockito.verify(containerSigningService, Mockito.times(1)).finalizeSignature(any(), eq(CONTAINER_ID), anyString(), any());
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
        DataToSignHolder dataToSignHolder = DataToSignHolder.builder()
                .dataFilesHash("someRandomHashFromBefore")
                .build();
        session.addDataToSign(SIG_ID, dataToSignHolder);
        getSigningService().finalizeSignature(session, CONTAINER_ID, SIG_ID, "b64".getBytes());
    }

    protected void assertFinalizeSignatureWithContainerDataFilesChangedClearsDataToSign() {
        Session session = getSimpleSessionHolderBuilder()
                .addDataFile("datafile.txt", "data")
                .build();
        DataToSignHolder dataToSignHolder = DataToSignHolder.builder()
                .dataFilesHash("someRandomHashFromBefore")
                .build();
        session.addDataToSign(SIG_ID, dataToSignHolder);

        ArgumentCaptor<String> containerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        Mockito.doNothing().when(sessionService).update(containerIdCaptor.capture(), sessionCaptor.capture());

        try {
            getSigningService().finalizeSignature(session, CONTAINER_ID, SIG_ID, "b64".getBytes());
        } catch (InvalidSessionDataException e) {
            //Error response not important for the test
        }

        String updatedContainerId = containerIdCaptor.getValue();
        Session updatedSession = sessionCaptor.getValue();

        Assert.assertEquals(CONTAINER_ID, updatedContainerId);
        Assert.assertEquals(CONTAINER_ID, updatedSession.getSessionId());
        Assert.assertNull(updatedSession.getDataToSignHolder(SIG_ID));
    }

    protected abstract ContainerSigningService getSigningService();

    protected abstract String getExpectedDataToSignPrefix();

    protected abstract void setSigningServiceParameters();

    protected abstract void mockRemoteSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract void mockMobileIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract void mockSmartIdSessionHolder(DataToSign dataToSign) throws IOException, URISyntaxException;

    protected abstract Session getSessionHolder() throws IOException, URISyntaxException;

    protected abstract SimpleSessionHolderBuilder getSimpleSessionHolderBuilder();

    protected interface SimpleSessionHolderBuilder {
        SimpleSessionHolderBuilder addDataFile(String fileName, String text);
        Session build();
    }

}
