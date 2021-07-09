package ee.openeid.siga.service.signature.smartid;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.SigaSmartIdException;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.util.encoders.Base64;
import org.digidoc4j.DataToSign;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SigaSmartIdClientTest {

    private static final String DEFAULT_MOCK_RELYING_PARTY_UUID = UUID.randomUUID().toString();
    private static final String DEFAULT_MOCK_SIGNATURE_BASE_64 = "c2lnbmF0dXJlMTIz";
    private static final String DEFAULT_MOCK_SIGNATURE = "signature123";
    private static final String DEFAULT_MOCK_DOCUMENT_NUMBER = "PNOEE-3725666666-QWER";
    private static final String DEFAULT_MOCK_RELYING_PARTY_NAME = "RELYING-PARTY";
    private static final String DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER = "1234567890";
    private static final byte[] DEFAULT_MOCK_DATA_TO_SIGN = "Data to be signed.".getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_MOCK_COUNTRY = "EE";
    private static final String DEFAULT_MOCK_DISPLAY_TEXT = "This is display text.";
    private static final String DEFAULT_MOCK_SESSION_ID = "de305d54-75b4-431b-adb2-eb6b9e546014";
    private static final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);
    @Mock
    private SmartIdServiceConfigurationProperties configurationProperties;
    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private SigaSmartIdClient smartIdClient;

    @Before
    public void setUp() throws IOException {
        Mockito.doReturn("http://localhost:" + wireMockRule.port()).when(configurationProperties).getUrl();
        Mockito.when(configurationProperties.getTruststorePath()).thenReturn("sid_truststore.p12");
        Mockito.when(configurationProperties.getTruststorePassword()).thenReturn("changeIt");
        Mockito.when(configurationProperties.getInteractionType()).thenReturn(SmartIdInteractionType.VERIFICATION_CODE_CHOICE);
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(SigaSmartIdClientTest.class.getClassLoader().getResource("sid_truststore.p12").openStream());
        Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(mockResource);
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }

    @Test
    public void initiateCertificateChoice_ok() {
        stubCertificateChoiceEtsiSessionResponse(200, "{\n" +
                "    \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        String sessionId = smartIdClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
    }

    @Test
    public void initiateCertificateChoice_excessFieldsInResponse_ok() {
        stubCertificateChoiceEtsiSessionResponse(200, "{\n" +
                "    \"excessText\": \"text\",\n" +
                "    \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\",\n" +
                "    \"excessNumber\": 7.3,\n" +
                "    \"excessBoolean\": true,\n" +
                "    \"excessObject\": {\n" +
                "        \"field\": \"value\"\n" +
                "    }\n" +
                "}");
        String sessionId = smartIdClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
    }

    @Test
    public void initiateCertificateChoice_forbidden() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");
        stubCertificateChoiceEtsiSessionResponse(403, "");
        String sessionId = smartIdClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
    }

    @Test
    public void initiateCertificateChoice_notFound() {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdErrorStatus.NOT_FOUND.getSigaMessage());
        stubCertificateChoiceEtsiSessionResponse(404, "");
        String sessionId = smartIdClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
    }

    @Test
    public void initiateCertificateChoice_serverError() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");

        stubCertificateChoiceErrorResponse(504);
        smartIdClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_ok() throws Exception {
        X509Certificate certificate = pkcs12Esteid2018SignatureToken.getCertificate();
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "OK");

        SmartIdCertificate response = smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(certificate, response.getCertificate());
    }

    @Test
    public void getCertificate_excessFieldsInResponse_ok() throws Exception {
        X509Certificate certificate = pkcs12Esteid2018SignatureToken.getCertificate();
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "    \"excessText\": \"text\",\n" +
                "    \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\",\n" +
                "    \"excessNumber\": 7.3,\n" +
                "    \"excessBoolean\": true,\n" +
                "    \"excessObject\": {\n" +
                "        \"field\": \"value\"\n" +
                "    }\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "OK");

        SmartIdCertificate response = smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
        Assert.assertEquals(certificate, response.getCertificate());
    }

    @Test
    public void getCertificate_notFound() {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdErrorStatus.NOT_FOUND.getSigaMessage());

        stubCertificateChoiceDocumentSessionResponse(404, "");
        smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_userRefused() throws CertificateEncodingException {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdSessionStatus.USER_REFUSED.getSigaSigningMessage());

        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED");
        smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_sessionTimeout() throws CertificateEncodingException {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdSessionStatus.TIMEOUT.getSigaSigningMessage());

        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "TIMEOUT");
        smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_documentUnusable() throws CertificateEncodingException {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdSessionStatus.DOCUMENT_UNUSABLE.getSigaSigningMessage());

        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "DOCUMENT_UNUSABLE");
        smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_forbidden() {
        expectGetCertificateGenericErrorForHttpCode(403);
    }

    @Test
    public void getCertificate_serverMaintenance() {
        expectGetCertificateGenericErrorForHttpCode(580);
    }

    @Test
    public void getCertificate_notSuitableAccount() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("No suitable account of requested type found, but user has some other accounts");

        stubGetStatusErrorResponse(471);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getCertificate_problemWithAccount() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Person should view app or self-service portal now");

        stubGetStatusErrorResponse(472);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getCertificate_clientNotSupported() {
        expectGetCertificateGenericErrorForHttpCode(480);
    }

    private void expectGetCertificateGenericErrorForHttpCode(int status) {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");


        stubCertificateChoiceDocumentSessionResponse(status, "");
        smartIdClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
    }

    @Test
    public void initSmartIdSigning_displayTextAndPin_ok() {
        Mockito.doReturn(SmartIdInteractionType.DISPLAY_TEXT_AND_PIN).when(configurationProperties).getInteractionType();
        stubSigningInitiationResponse(
                WireMock.equalToJson("{\n" +
                                "    \"allowedInteractionsOrder\": [\n" +
                                "        {\n" +
                                "            \"type\": \"displayTextAndPIN\",\n" +
                                "            \"displayText60\": \"This is display text.\"\n" +
                                "        }\n" +
                                "    ]\n" +
                                "}",
                        true,
                        true
                ),
                200,
                "{\"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"}"
        );

        InitSmartIdSignatureResponse response = smartIdClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        Assert.assertEquals(challengeId, response.getChallengeId());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
    }

    @Test
    public void initSmartIdSigning_verificationCodeChoice_ok() {
        Mockito.doReturn(SmartIdInteractionType.VERIFICATION_CODE_CHOICE).when(configurationProperties).getInteractionType();
        stubSigningInitiationResponse(
                WireMock.equalToJson("{\n" +
                                "    \"allowedInteractionsOrder\": [\n" +
                                "        {\n" +
                                "            \"type\": \"verificationCodeChoice\",\n" +
                                "            \"displayText60\": \"This is display text.\"\n" +
                                "        }\n" +
                                "    ]\n" +
                                "}",
                        true,
                        true
                ),
                200,
                "{\"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"}"
        );

        InitSmartIdSignatureResponse response = smartIdClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        Assert.assertEquals(challengeId, response.getChallengeId());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
    }

    @Test
    public void initSmartIdSigning_excessFieldsInResponse_ok() {
        stubSigningInitiationResponse(200, "{\n" +
                "    \"excessText\": \"text\",\n" +
                "    \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\",\n" +
                "    \"excessNumber\": 7.3,\n" +
                "    \"excessBoolean\": true,\n" +
                "    \"excessObject\": {\n" +
                "        \"field\": \"value\"\n" +
                "    }\n" +
                "}");

        InitSmartIdSignatureResponse response = smartIdClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        Assert.assertEquals(challengeId, response.getChallengeId());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
    }

    @Test
    public void initSmartIdSigning_forbidden() {
        expectInitSmartIdSigningGenericErrorForHttpCode(403);
    }

    @Test
    public void initSmartIdSigning_notFound() {
        expectInitSmartIdSigningGenericErrorForHttpCode(404);
    }

    @Test
    public void initSmartIdSigning_notSuitableAccount() {
        expectInitSmartIdSigningGenericErrorForHttpCode(471);
    }

    @Test
    public void initSmartIdSigning_problemWithAccount() {
        expectInitSmartIdSigningGenericErrorForHttpCode(472);
    }

    @Test
    public void initSmartIdSigning_clientNotSupported() {
        expectInitSmartIdSigningGenericErrorForHttpCode(480);
    }

    @Test
    public void initSmartIdSigning_serverError() {
        expectInitSmartIdSigningGenericErrorForHttpCode(504);
    }

    @Test
    public void initSmartIdSigning_serverMaintenance() {
        expectInitSmartIdSigningGenericErrorForHttpCode(580);
    }

    private void expectInitSmartIdSigningGenericErrorForHttpCode(int status) {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");


        smartIdClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
    }

    @Test
    public void getStatus_running() {
        stubGetSession(200, "{\n" +
                "    \"state\": \"RUNNING\"\n" +
                "}");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.RUNNING, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_excessFieldsInResponse_running() {
        stubGetSession(200, "{\n" +
                "    \"excessText\": \"text\",\n" +
                "    \"excessNumber\": 7.3,\n" +
                "    \"state\": \"RUNNING\",\n" +
                "    \"excessBoolean\": true,\n" +
                "    \"excessObject\": {\n" +
                "        \"field\": \"value\"\n" +
                "    }\n" +
                "}");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.RUNNING, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_ok() throws CertificateEncodingException {
        stubGetSession("COMPLETE", "OK");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.OK, response.getStatus());
        Assert.assertArrayEquals(DEFAULT_MOCK_SIGNATURE.getBytes(), response.getSignature());
    }

    @Test
    public void getStatus_excessFieldsInResponse_ok() throws CertificateEncodingException {
        stubGetSession(200, "{\n" +
                "    \"excessText\": \"text\",\n" +
                "    \"state\": \"COMPLETE\",\n" +
                "    \"excessNumber\": 7.3,\n" +
                "    \"result\": {\n" +
                "            \"endResult\": \"OK\",\n" +
                "            \"excessText\": \"text\",\n" +
                "            \"documentNumber\": \"" + DEFAULT_MOCK_DOCUMENT_NUMBER + "\"\n" +
                "    },\n" +
                "    \"excessBoolean\": true,\n" +
                "    \"signature\": {\n" +
                "        \"excessNumber\": 0,\n" +
                "        \"value\": \"" + DEFAULT_MOCK_SIGNATURE_BASE_64 + "\",\n" +
                "        \"algorithm\": \"sha512WithRSAEncryption\"\n" +
                "    },\n" +
                "    \"excessArray\": [ 1.2 ],\n" +
                "    \"cert\": {\n" +
                "        \"value\": \"" + new String(Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                "        \"certificateLevel\": \"ADVANCED\",\n" +
                "        \"excessBoolean\": false\n" +
                "    },\n" +
                "    \"excessObject\": {\n" +
                "        \"field\": \"value\"\n" +
                "    }\n" +
                "}");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.OK, response.getStatus());
        Assert.assertArrayEquals(DEFAULT_MOCK_SIGNATURE.getBytes(), response.getSignature());
    }

    @Test
    public void getStatus_timeout() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "TIMEOUT");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.TIMEOUT, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefused() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefusedCertChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CERT_CHOICE");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED_CERT_CHOICE, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefusedConfirmationMessage() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CONFIRMATIONMESSAGE");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefusedConfirmationMessageWithVerificationCodeChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefusedDisplayTextAndPin() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_DISPLAYTEXTANDPIN");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED_DISPLAYTEXTANDPIN, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userRefusedVerificationCodeChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_VC_CHOICE");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.USER_REFUSED_VC_CHOICE, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_userChoseWrongVerificationCode() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "WRONG_VC");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.WRONG_VC, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_documentUnusable() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "DOCUMENT_UNUSABLE");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.DOCUMENT_UNUSABLE, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_notSupportedByApp() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP");

        SmartIdStatusResponse response = smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals(SmartIdSessionStatus.REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_unexpectedState() throws CertificateEncodingException {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service returned unexpected response");

        stubGetSessionOkResponseWithoutSignature("RANDOM123", "OK");
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
    }

    @Test
    public void getStatus_unexpectedStatus() throws CertificateEncodingException {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service returned unexpected response");

        stubGetSessionOkResponseWithoutSignature("COMPLETE", "RANDOM123");
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
    }

    @Test
    public void getStatus_forbidden() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");

        stubGetStatusErrorResponse(403);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getStatus_sessionNotFound() {
        exceptionRule.expect(SigaSmartIdException.class);
        exceptionRule.expectMessage(SmartIdErrorStatus.SESSION_NOT_FOUND.getSigaMessage());

        stubGetStatusErrorResponse(404);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getStatus_serverError() {
        expectGetStatusGenericErrorForHttpCode(504);
    }

    @Test
    public void getStatus_serverMaintenance() {
        expectGetStatusGenericErrorForHttpCode(580);
    }

    @Test
    public void getStatus_notSuitableAccount() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("No suitable account of requested type found, but user has some other accounts");
        stubGetStatusErrorResponse(471);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getStatus_problemWithAccount() {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Person should view app or self-service portal now");
        stubGetStatusErrorResponse(472);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    @Test
    public void getStatus_clientNotSupported() {
        expectGetStatusGenericErrorForHttpCode(480);
    }

    @Test
    public void getStatus_unableToParseSignature() throws CertificateEncodingException {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service returned unexpected response");

        stubGetSession("COMPLETE", "OK", "12345");
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
    }

    private void expectGetStatusGenericErrorForHttpCode(int status) {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Smart-ID service error");


        stubGetStatusErrorResponse(status);
        smartIdClient.getSmartIdCertificateStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER);
    }

    private void stubSigningInitiationResponse(ContentPattern<?> requestBody, int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/signature/document/" + DEFAULT_MOCK_DOCUMENT_NUMBER)
                        .withRequestBody(requestBody)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubSigningInitiationResponse(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/signature/document/" + DEFAULT_MOCK_DOCUMENT_NUMBER)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubCertificateChoiceDocumentSessionResponse(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/certificatechoice/document/" + DEFAULT_MOCK_DOCUMENT_NUMBER)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubCertificateChoiceEtsiSessionResponse(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/certificatechoice/etsi/PNO" + DEFAULT_MOCK_COUNTRY + "-" + DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubCertificateChoiceErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.post("/certificatechoice/etsi/PNO" + DEFAULT_MOCK_COUNTRY + "-" + DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)));
    }

    private void stubGetStatusErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/session/" + DEFAULT_MOCK_DOCUMENT_NUMBER))
                        .willReturn(WireMock.aResponse().withStatus(status))
        );
    }

    private void stubGetSessionOkResponseWithoutSignature(String state, String result) throws CertificateEncodingException {
        stubGetSession(200, "{\n" +
                "    \"state\": \"" + state + "\",\n" +
                "    \"result\": {\n" +
                "            \"endResult\": \"" + result + "\",\n" +
                "            \"documentNumber\": \"" + DEFAULT_MOCK_DOCUMENT_NUMBER + "\"\n" +
                "    },\n" +
                "    \"cert\": {\n" +
                "        \"value\": \"" + new String(Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                "        \"certificateLevel\": \"ADVANCED\"\n" +
                "    }\n" +
                "}"
        );
    }

    private void stubGetSession(String state, String result) throws CertificateEncodingException {
        stubGetSession(state, result, DEFAULT_MOCK_SIGNATURE_BASE_64);
    }

    private void stubGetSession(String state, String result, String signature) throws CertificateEncodingException {
        stubGetSession(200, "{\n" +
                "    \"state\": \"" + state + "\",\n" +
                "    \"result\": {\n" +
                "            \"endResult\": \"" + result + "\",\n" +
                "            \"documentNumber\": \"" + DEFAULT_MOCK_DOCUMENT_NUMBER + "\"\n" +
                "    },\n" +
                "    \"signature\": {\n" +
                "        \"value\": \"" + signature + "\",\n" +
                "        \"algorithm\": \"sha512WithRSAEncryption\"\n" +
                "    },\n" +
                "    \"cert\": {\n" +
                "        \"value\": \"" + new String(Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                "        \"certificateLevel\": \"ADVANCED\"\n" +
                "    }\n" +
                "}"
        );
    }

    private void stubGetSession(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.get("/session/" + DEFAULT_MOCK_SESSION_ID)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private DataToSign mockDataToSign(byte[] dataToBeSigned) {
        DataToSign dataToSignMock = Mockito.mock(DataToSign.class);
        Mockito.doReturn(dataToBeSigned).when(dataToSignMock).getDataToSign();
        return dataToSignMock;
    }

    private SmartIdInformation createDefaultSmartIdInformation() {
        return SmartIdInformation.builder()
                .personIdentifier(DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER)
                .messageToDisplay(DEFAULT_MOCK_DISPLAY_TEXT)
                .country(DEFAULT_MOCK_COUNTRY)
                .documentNumber(DEFAULT_MOCK_DOCUMENT_NUMBER)
                .build();
    }

    private RelyingPartyInfo createRPInfo(){
        return RelyingPartyInfo.builder()
                .name(DEFAULT_MOCK_RELYING_PARTY_NAME)
                .uuid(DEFAULT_MOCK_RELYING_PARTY_UUID)
                .build();
    }
}
