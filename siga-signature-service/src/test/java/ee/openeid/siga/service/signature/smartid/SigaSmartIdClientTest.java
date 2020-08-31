package ee.openeid.siga.service.signature.smartid;


import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.sk.smartid.HashType;
import ee.sk.smartid.SignableHash;
import ee.sk.smartid.SmartIdCertificate;
import ee.sk.smartid.exception.CertificateNotFoundException;
import ee.sk.smartid.rest.dao.SessionStatus;
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
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class SigaSmartIdClientTest {

    private static final String DEFAULT_MOCK_RELYING_PARTY_UUID = UUID.randomUUID().toString();
    private static final String DEFAULT_MOCK_SIGNATURE = "NSbm9rdA2dBmBJHrwLDrXguPFoQ9IzSODCIP2FZixCAUMPSZEDkYzT36kwW0FioTNCfZBEThZtTqRUvwRdO91P4bPw7iph+fo1DafdiJUbZcO/5T65nFh+D9diONEiN90Viy6qGFbKm9sYBRPmuEYYiWJc3E1PDGMCBrJjX6qumJ2CacJ3nFKqXIchBID5euh5Q0w70lDToxhgOCraALEmz9huaPxYIVth5lurYXG0fCfoU/laxiql0EIQuxgwXUaIYTEv+sZWKC2vj0Iy51a66COwkwdjGe/niqhXW3KrisKjoqTR61CN7M5/xB1vupN2Q+WMnRde1sdBq8oIAnz5u8X+u5AiVq8OySE++1vSoorAId/sc7szlfACLYZu6tFDXYxrkP4eEjVf9jvIwNn/c6wa4kQ0LX1NMDtZqSp5ncqmmPDaZB9ROg3buz7DMVJ5EVqdqFu/4PQYb6FC+0fBuTBz3j7kFE9SoyzeIvmtpR9vRcs849zf0Ff0EWoOd9EEG/MfFOiVlgL57qXj4hWD4fsWXiqpNE5xr0SzJmPqlY7PqGqonoZsF5Urr+3RYdHJQCVbFxcC/QNHrk5zf26skmcmyUYKFvV3PO+UAFZun/H2ZrBm/q0ZaRy5dHhFDIUw5fXIkOlvieWoU5q8n5D0D4uIjMo1oJTbVAM5iVntE=";
    private static final String DEFAULT_MOCK_DOCUMENT_NUMBER = "PNOEE-3725666666";
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

    @InjectMocks
    private SigaSmartIdClient smartIdClient;

    @Before
    public void setUp() {
        Mockito.doReturn("http://localhost:" + wireMockRule.port()).when(configurationProperties).getUrl();
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }

    @Test
    public void getCertificate_smartIdReturnsOk() throws Exception {
        X509Certificate certificate = pkcs12Esteid2018SignatureToken.getCertificate();
        stubCertificateChoiceSessionResponse(200, "{\n" +
                "      \"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"\n" +
                "}");
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "OK");

        SmartIdCertificate response = smartIdClient.getCertificate(createDefaultSmartIdInformation());
        Assert.assertEquals(certificate, response.getCertificate());
    }

    @Test
    public void getCertificate_certificateChoiceNotFound() {
        exceptionRule.expect(CertificateNotFoundException.class);
        stubCertificateChoiceSessionResponse(404, "");
        smartIdClient.getCertificate(createDefaultSmartIdInformation());
    }

    @Test
    public void getCertificate_midRestReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubCertificateChoiceSessionResponse(status.value(), "");
            try {
                smartIdClient.getCertificate(createDefaultSmartIdInformation());
                Assert.fail("Should not reach here");
            } catch (ClientException e) {
                Assert.assertEquals("Smart-ID service error", e.getMessage());
            }
            WireMock.reset();
        });
    }

    @Test
    public void initSmartIdSigning_smartIdReturnsValidResponse() {
        stubSigningInitiationResponse(200, "{\"sessionID\": \"" + DEFAULT_MOCK_SESSION_ID + "\"}");

        InitSmartIdSignatureResponse response = smartIdClient.initSmartIdSigning(createDefaultSmartIdInformation(), mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN), DEFAULT_MOCK_DOCUMENT_NUMBER);
        SignableHash signableHash = new SignableHash();
        signableHash.setHash(DigestUtils.sha512(DEFAULT_MOCK_DATA_TO_SIGN));
        signableHash.setHashType(HashType.SHA512);
        String challengeId = signableHash.calculateVerificationCode();
        Assert.assertEquals(challengeId, response.getChallengeId());
        Assert.assertEquals(DEFAULT_MOCK_SESSION_ID, response.getSessionCode());
    }

    @Test
    public void initSmartIdSigning_smartIdReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubSigningInitiationResponse(status.value(), "");
            try {
                smartIdClient.initSmartIdSigning(createDefaultSmartIdInformation(), mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN), DEFAULT_MOCK_DOCUMENT_NUMBER);
                Assert.fail("Should not reach here");
            } catch (ClientException e) {
                Assert.assertEquals("Smart-ID service error", e.getMessage());
            }
            WireMock.reset();
        });
    }

    @Test
    public void getStatus_smartIdReturnsRunning() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("RUNNING", "OK");

        SessionStatus response = smartIdClient.getSmartIdStatus(createDefaultSmartIdInformation(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals("RUNNING", response.getState());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_smartIdReturnsCompleteAndOk() throws CertificateEncodingException {
        stubGetSession("COMPLETE", "OK");

        SessionStatus response = smartIdClient.getSmartIdStatus(createDefaultSmartIdInformation(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals("COMPLETE", response.getState());
        Assert.assertEquals(DEFAULT_MOCK_SIGNATURE, response.getSignature().getValue());
    }

    @Test
    public void getStatus_smartIdReturnsCompleteAndTimeout() throws CertificateEncodingException {
        stubGetSessionOkResponseWithoutSignature("COMPLETE", "TIMEOUT");

        SessionStatus response = smartIdClient.getSmartIdStatus(createDefaultSmartIdInformation(), DEFAULT_MOCK_SESSION_ID);
        Assert.assertEquals("COMPLETE", response.getState());
        Assert.assertEquals("TIMEOUT", response.getResult().getEndResult());
    }

    @Test
    public void getStatus_smartIdReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubGetStatusErrorResponse(status.value());
            try {
                smartIdClient.getSmartIdStatus(createDefaultSmartIdInformation(), DEFAULT_MOCK_DOCUMENT_NUMBER);
                Assert.fail("Should not reach here");
            } catch (ClientException e) {
                Assert.assertEquals("Smart-ID service error", e.getMessage());
            }
            WireMock.reset();
        });
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

    private void stubCertificateChoiceSessionResponse(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/certificatechoice/pno/EE/" + DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER)
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubGetStatusErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/session/" + DEFAULT_MOCK_DOCUMENT_NUMBER))
                        .willReturn(WireMock.aResponse().withStatus(status))
        );
    }

    private void stubGetSessionOkResponseWithoutSignature(String state, String result) throws CertificateEncodingException {
        WireMock.stubFor(
                WireMock.get("/session/" + DEFAULT_MOCK_SESSION_ID)
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "    \"state\": \"" + state + "\",\n" +
                                        "\t\"result\": {\n" +
                                        "            \"endResult\": \"" + result + "\",\n" +
                                        "            \"documentNumber\": \"" + DEFAULT_MOCK_DOCUMENT_NUMBER + "\"\n" +
                                        "    },\n" +
                                        "    \"cert\": {\n" +
                                        "        \"value\": \"" + new String(Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                                        "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                                        "\t\t\"certificateLevel\": \"ADVANCED\"\n" +
                                        "    }\n" +
                                        "}"))
        );
    }

    private void stubGetSession(String state, String result) throws CertificateEncodingException {
        WireMock.stubFor(
                WireMock.get("/session/" + DEFAULT_MOCK_SESSION_ID)
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                        "    \"state\": \"" + state + "\",\n" +
                                        "\t\"result\": {\n" +
                                        "            \"endResult\": \"" + result + "\",\n" +
                                        "            \"documentNumber\": \"" + DEFAULT_MOCK_DOCUMENT_NUMBER + "\"\n" +
                                        "    },\n" +
                                        "    \"signature\": {\n" +
                                        "        \"value\": \"" + DEFAULT_MOCK_SIGNATURE + "\",\n" +
                                        "        \"algorithm\": \"sha512WithRSAEncryption\"\n" +
                                        "    },\n" +
                                        "    \"cert\": {\n" +
                                        "        \"value\": \"" + new String(Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                                        "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                                        "\t\t\"certificateLevel\": \"ADVANCED\"\n" +
                                        "    }\n" +
                                        "}"))
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
                .relyingPartyUuid(DEFAULT_MOCK_RELYING_PARTY_UUID)
                .relyingPartyName(DEFAULT_MOCK_RELYING_PARTY_NAME)
                .build();
    }
}
