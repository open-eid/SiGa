package ee.openeid.siga.service.signature.smartid;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.SmartIdApiException;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.util.encoders.Base64;
import org.digidoc4j.DataToSign;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.ServerErrorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@WireMockTest
public class SmartIdApiClientTest {

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

    @Mock
    private SmartIdClientConfigurationProperties configurationProperties;
    @Mock
    private ResourceLoader resourceLoader;

    @InjectMocks
    private SmartIdApiClient smartIdApiClient;

    @BeforeEach
    public void setUp(WireMockRuntimeInfo wireMockServer) throws IOException {
        Mockito.doReturn("http://localhost:" + wireMockServer.getHttpPort()).when(configurationProperties).getUrl();
        Mockito.when(configurationProperties.getTruststorePath()).thenReturn("sid_truststore.p12");
        Mockito.when(configurationProperties.getTruststorePassword()).thenReturn("changeIt");
        Mockito.when(configurationProperties.getSessionStatusResponseSocketOpenTime()).thenReturn(Duration.ofMillis(30000));
        Mockito.lenient().when(configurationProperties.getInteractionType()).thenReturn(SmartIdInteractionType.VERIFICATION_CODE_CHOICE);
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(SmartIdApiClientTest.class.getClassLoader().getResource("sid_truststore.p12").openStream());
        Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(mockResource);
    }

    @AfterEach
    public void tearDown() {
        WireMock.reset();
    }

    @Test
    public void initiateCertificateChoice_ok() {
        stubCertificateChoiceEtsiSessionResponse(200, "{\n" +
                "    \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        String sessionId = smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
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
        String sessionId = smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation());
        assertEquals(DEFAULT_MOCK_SESSION_ID, sessionId);
    }

    @Test
    public void initiateCertificateChoice_forbidden() {
        stubCertificateChoiceEtsiSessionResponse(403, "");
        Exception exception = assertThrows(ClientException.class, () -> smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo("Smart-ID service error"));
    }

    @Test
    public void initiateCertificateChoice_notFound() {
        stubCertificateChoiceEtsiSessionResponse(404, "");
        SmartIdApiException exception = assertThrows(SmartIdApiException.class, () -> smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo(SmartIdErrorStatus.NOT_FOUND.getSigaMessage()));
    }

    @Test
    public void initiateCertificateChoice_noSuitableAccount() {
        stubCertificateChoiceEtsiSessionResponse(471, "");
        ClientException exception = assertThrows(ClientException.class, () -> smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo("No suitable account of requested type found, but user has some other accounts"));
    }

    @Test
    public void initiateCertificateChoice_personShouldViewPortal() {
        stubCertificateChoiceEtsiSessionResponse(472, "");
        ClientException exception = assertThrows(ClientException.class, () -> smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo("Person should view app or self-service portal now"));
    }

    @Test
    public void initiateCertificateChoice_serverError() {
        stubCertificateChoiceErrorResponse(504);
        Exception exception = assertThrows(ClientException.class, () -> smartIdApiClient.initiateCertificateChoice(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo("Smart-ID service error"));
    }

    @Test
    public void getCertificate_ok() throws Exception {
        X509Certificate certificate = pkcs12Esteid2018SignatureToken.getCertificate();
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "OK");

        SmartIdCertificate response = smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
        assertEquals(certificate, response.getCertificate());
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

        SmartIdCertificate response = smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation());
        assertEquals(certificate, response.getCertificate());
    }

    @Test
    public void getCertificate_userRefused() throws CertificateEncodingException {
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED");

        RuntimeException exception = assertThrows(SmartIdApiException.class, () -> smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation()));

        assertThat(exception.getMessage(), equalTo(SmartIdSessionStatus.USER_REFUSED.getSigaSigningMessage()));
    }

    @Test
    public void getCertificate_sessionTimeout() throws CertificateEncodingException {
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "TIMEOUT");

        RuntimeException exception = assertThrows(SmartIdApiException.class, () -> smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation()));

        assertThat(exception.getMessage(), equalTo(SmartIdSessionStatus.TIMEOUT.getSigaSigningMessage()));
    }

    @Test
    public void getCertificate_documentUnusable() throws CertificateEncodingException {
        stubCertificateChoiceDocumentSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "DOCUMENT_UNUSABLE");

        RuntimeException exception = assertThrows(SmartIdApiException.class, () -> smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation()));

        assertThat(exception.getMessage(), equalTo(SmartIdSessionStatus.DOCUMENT_UNUSABLE.getSigaSigningMessage()));
    }

    @Test
    public void getCertificate_forbidden() {
        assertGetCertificateGenericException(403);
    }

    @Test
    public void getCertificate_notFound() {
        assertGetCertificateException(404, SmartIdApiException.class, SmartIdErrorStatus.NOT_FOUND.getSigaMessage());
    }

    @Test
    public void getCertificate_noSuitableAccount() {
        assertGetCertificateException(471, ClientException.class, "No suitable account of requested type found, but user has some other accounts");
    }

    @Test
    public void getCertificate_personShouldViewPortal() {
        assertGetCertificateException(472, ClientException.class, "Person should view app or self-service portal now");
    }

    @Test
    public void getCertificate_serverMaintenance() {
        assertGetCertificateGenericException(580);
    }

    @Test
    public void getCertificate_clientNotSupported() {
        assertGetCertificateGenericException(480);
    }

    private void assertGetCertificateGenericException(int status) {
        assertGetCertificateException(status, ClientException.class, "Smart-ID service error");
    }

    private void assertGetCertificateException(int status, Class<? extends RuntimeException> expectedException, String expectedMessage) {
        stubCertificateChoiceDocumentSessionResponse(status, "");
        RuntimeException exception = assertThrows(expectedException, () -> smartIdApiClient.getCertificate(createRPInfo(), createDefaultSmartIdInformation()));
        assertThat(exception.getMessage(), equalTo(expectedMessage));
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

        InitSmartIdSignatureResponse response = smartIdApiClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        assertEquals(challengeId, response.getChallengeId());
        assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
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

        InitSmartIdSignatureResponse response = smartIdApiClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        assertEquals(challengeId, response.getChallengeId());
        assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
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

        InitSmartIdSignatureResponse response = smartIdApiClient.initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(),
                mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN));
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        assertEquals(challengeId, response.getChallengeId());
        assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
    }

    @Test
    public void initSmartIdSigning_forbidden() {
        assertInitSmartIdSigningGenericException(403);
    }

    @Test
    public void initSmartIdSigning_notFound() {
        assertInitSmartIdSigningGenericException(404);
    }

    @Test
    public void initSmartIdSigning_noSuitableAccount() {
        assertInitSmartIdSigningException(471, ClientException.class, "No suitable account of requested type found, but user has some other accounts");
    }

    @Test
    public void initSmartIdSigning_personShouldViewPortal() {
        assertInitSmartIdSigningException(472, ClientException.class, "Person should view app or self-service portal now");
    }

    @Test
    public void initSmartIdSigning_clientNotSupported() {
        assertInitSmartIdSigningGenericException(480);
    }

    @Test
    public void initSmartIdSigning_serverError() {
        assertInitSmartIdSigningGenericException(504);
    }

    @Test
    public void initSmartIdSigning_serverMaintenance() {
        assertInitSmartIdSigningGenericException(580);
    }

    private void assertInitSmartIdSigningGenericException(int status) {
        assertInitSmartIdSigningException(status, ClientException.class, "Smart-ID service error");
    }

    private void assertInitSmartIdSigningException(int status, Class<? extends RuntimeException> expectedException, String expectedMessage) {
        stubSigningInitiationResponse(status, "{\"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"}");

        RuntimeException exception = assertThrows(expectedException, () -> smartIdApiClient
                .initSmartIdSigning(createRPInfo(), createDefaultSmartIdInformation(), mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN)));

        assertThat(exception.getMessage(), equalTo(expectedMessage));
    }

    @Test
    public void getSessionStatus_ok() throws CertificateEncodingException {
        stubGetSession("COMPLETE", "OK");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.OK, response.getStatus());
        assertArrayEquals(DEFAULT_MOCK_SIGNATURE.getBytes(), response.getSignature());
    }

    @Test
    public void getSessionStatus_excessFieldsInResponse_ok() throws CertificateEncodingException {
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

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.OK, response.getStatus());
        assertArrayEquals(DEFAULT_MOCK_SIGNATURE.getBytes(), response.getSignature());
    }

    @Test
    public void getSessionStatus_timeout() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "TIMEOUT");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.TIMEOUT, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefused() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefusedCertChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CERT_CHOICE");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED_CERT_CHOICE, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefusedConfirmationMessage() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CONFIRMATIONMESSAGE");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefusedConfirmationMessageWithVerificationCodeChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED_CONFIRMATIONMESSAGE_WITH_VC_CHOICE, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefusedDisplayTextAndPin() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_DISPLAYTEXTANDPIN");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED_DISPLAYTEXTANDPIN, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userRefusedVerificationCodeChoice() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "USER_REFUSED_VC_CHOICE");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.USER_REFUSED_VC_CHOICE, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_userChoseWrongVerificationCode() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "WRONG_VC");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.WRONG_VC, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_documentUnusable() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "DOCUMENT_UNUSABLE");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.DOCUMENT_UNUSABLE, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_notSupportedByApp() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP");

        SmartIdStatusResponse response = smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID);
        assertEquals(SmartIdSessionStatus.REQUIRED_INTERACTION_NOT_SUPPORTED_BY_APP, response.getStatus());
        assertNull(response.getSignature());
    }

    @Test
    public void getSessionStatus_unexpectedState() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("RANDOM123", "OK");

        ClientException exception = assertThrows(ClientException.class, () -> smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID));

        assertThat(exception.getCause().getMessage(), equalTo("Smart-ID service responded with unexpected state: RANDOM123"));
    }

    @Test
    public void getSessionStatus_unexpectedResult() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "RANDOM123");

        ClientException exception = assertThrows(ClientException.class, () -> smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID));

        assertThat(exception.getCause().getMessage(), equalTo("Smart-ID service responded with unexpected end result: RANDOM123"));
    }

    @Test
    public void getSessionStatus_forbidden() {
        assertGetSessionStatusException(403, ForbiddenException.class, "HTTP 403 Forbidden");
    }

    @Test
    public void getSessionStatus_sessionNotFound() {
        assertGetSessionStatusException(404, SmartIdApiException.class, SmartIdErrorStatus.SESSION_NOT_FOUND.getSigaMessage());
    }

    @Test
    public void getSessionStatus_serverError() {
        assertGetSessionStatusException(504, ServerErrorException.class, "HTTP 504 Gateway Timeout");
    }

    @Test
    public void getSessionStatus_serverMaintenance() {
        assertGetSessionStatusException(580, ServerErrorException.class, "HTTP 580 580");
    }

    @Test
    public void getSessionStatus_notSuitableAccount() {
        assertGetSessionStatusException(471, ClientException.class, "No suitable account of requested type found, but user has some other accounts");
    }

    @Test
    public void getSessionStatus_problemWithAccount() {
        assertGetSessionStatusException(472, ClientException.class, "Person should view app or self-service portal now");
    }

    @Test
    public void getStatus_clientNotSupported() {
        assertGetSessionStatusException(480, ClientErrorException.class, "HTTP 480 480");
    }

    @Test
    public void getSessionStatus_unableToParseSignature() throws CertificateEncodingException {
        stubGetSession("COMPLETE", "OK", "12345");

        ClientException exception = assertThrows(ClientException.class, () -> smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_SESSION_ID));

        assertThat(exception.getMessage(), equalTo("Smart-ID service returned unexpected response"));
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
                WireMock.get(WireMock.urlPathEqualTo("/session/" + DEFAULT_MOCK_SESSION_ID))
                        .withQueryParam("timeoutMs", WireMock.equalTo("30000"))
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

    private RelyingPartyInfo createRPInfo() {
        return RelyingPartyInfo.builder()
                .name(DEFAULT_MOCK_RELYING_PARTY_NAME)
                .uuid(DEFAULT_MOCK_RELYING_PARTY_UUID)
                .build();
    }

    private void assertGetSessionStatusException(int status, Class<? extends RuntimeException> expectedException, String expectedMessage) {
        stubGetStatusErrorResponse(status);
        RuntimeException exception = assertThrows(expectedException, () -> smartIdApiClient.getSessionStatus(createRPInfo(), DEFAULT_MOCK_DOCUMENT_NUMBER));
        assertThat(exception.getMessage(), equalTo(expectedMessage));
    }
}
