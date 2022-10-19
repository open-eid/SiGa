package ee.openeid.siga.service.signature.mobileid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.MobileIdApiException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.RelyingPartyInfo;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClientTest;
import ee.sk.mid.MidVerificationCodeCalculator;
import org.apache.commons.codec.digest.DigestUtils;
import org.digidoc4j.DataToSign;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

import javax.ws.rs.ServerErrorException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.junit.Assert.assertThrows;

@RunWith(MockitoJUnitRunner.class)
public class MobileIdApiClientTest {

    private static final String DEFAULT_MOCK_SESSION_CODE = "mock-session-code";
    private static final UUID DEFAULT_MOCK_RELYING_PARTY_UUID = UUID.randomUUID();
    private static final String DEFAULT_MOCK_RELYING_PARTY_NAME = "RELYING-PARTY";
    private static final String DEFAULT_MOCK_PHONE_NUMBER = "+3721234567";
    private static final String DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER = "1234567890";
    private static final String DEFAULT_MOCK_LANGUAGE = "ENG";
    private static final String DEFAULT_MOCK_DISPLAY_TEXT = "This is display text.";
    private static final byte[] DEFAULT_MOCK_DATA_TO_SIGN = "Data to be signed.".getBytes(StandardCharsets.UTF_8);
    private static final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);
    @Mock
    private MobileIdClientConfigurationProperties configurationProperties;
    @InjectMocks
    private MobileIdApiClient mobileIdApiClient;
    @Mock
    private ResourceLoader resourceLoader;
    @Mock
    private Resource resource;

    @Before
    public void setUp() throws IOException {
        Mockito.doReturn("http://localhost:" + wireMockRule.port()).when(configurationProperties).getUrl();
        Mockito.when(configurationProperties.getTruststorePath()).thenReturn("mid_truststore.p12");
        Mockito.when(configurationProperties.getTruststorePassword()).thenReturn("changeIt");
        Mockito.when(configurationProperties.getLongPollingTimeout()).thenReturn(Duration.ofMillis(30000));
        Mockito.when(resource.getInputStream()).thenReturn(SmartIdApiClientTest.class.getClassLoader().getResource("mid_truststore.p12").openStream());
        Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }

    @Test
    public void getCertificate_midRestReturnsOk() throws Exception {
        X509Certificate certificate = pkcs12Esteid2018SignatureToken.getCertificate();
        stubCertificateRequestOkResponse("{" +
                "\"result\": \"OK\"," +
                "\"cert\": \"" + Base64.getEncoder().encodeToString(certificate.getEncoded()) + "\"" +
                "}");

        X509Certificate response = mobileIdApiClient.getCertificate(createRPInfo(), createDefaultMobileIdInformation());
        Assert.assertEquals(certificate, response);
    }

    @Test
    public void getCertificate_midRestReturnsNotFound() {
        stubCertificateRequestOkResponse("{\"result\": \"NOT_FOUND\"}");
        try {
            mobileIdApiClient.getCertificate(createRPInfo(), createDefaultMobileIdInformation());
            Assert.fail("Should not reach here");
        } catch (MobileIdApiException e) {
            Assert.assertEquals("NOT_FOUND", e.getMessage());
        }
    }

    @Test
    public void getCertificate_midRestReturnsUnexpectedResult() {
        stubCertificateRequestOkResponse("{\"result\": \"INVALID_RESULT\"}");
        try {
            mobileIdApiClient.getCertificate(createRPInfo(), createDefaultMobileIdInformation());
            Assert.fail("Should not reach here");
        } catch (MobileIdApiException e) {
            Assert.assertEquals("UNEXPECTED_STATUS", e.getMessage());
        }
    }

    @Test
    public void getCertificate_midRestReturns400() {
        stubCertificateRequestErrorResponse(400);
        try {
            mobileIdApiClient.getCertificate(createRPInfo(), createDefaultMobileIdInformation());
            Assert.fail("Should not reach here");
        } catch (MobileIdApiException e) {
            Assert.assertEquals("NOT_FOUND", e.getMessage());
        }
    }

    @Test
    public void getCertificate_midRestReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubCertificateRequestErrorResponse(status.value());
            try {
                Mockito.when(resource.getInputStream()).thenReturn(SmartIdApiClientTest.class.getClassLoader().getResource("mid_truststore.p12").openStream());
                mobileIdApiClient.getCertificate(createRPInfo(), createDefaultMobileIdInformation());
                Assert.fail("Should not reach here");
            } catch (ClientException | IOException e) {
                Assert.assertEquals("Mobile-ID service error", e.getMessage());
            }
            WireMock.reset();
        });
    }

    @Test
    public void initMobileSigning_midRestReturnsValidResponse() {
        stubSigningInitiationOkResponse("{\"sessionID\": \"session-id-value\"}");

        InitMidSignatureResponse response = mobileIdApiClient.initMobileSigning(createRPInfo(), mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN), createDefaultMobileIdInformation());
        Assert.assertEquals(MidVerificationCodeCalculator.calculateMobileIdVerificationCode(DigestUtils.sha256(DEFAULT_MOCK_DATA_TO_SIGN)), response.getChallengeId());
        Assert.assertEquals("session-id-value", response.getSessionCode());
    }

    @Test
    public void initMobileSigning_midRestReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubSigningInitiationErrorResponse(status.value());
            try {
                Mockito.when(resource.getInputStream()).thenReturn(SmartIdApiClientTest.class.getClassLoader().getResource("mid_truststore.p12").openStream());
                mobileIdApiClient.initMobileSigning(createRPInfo(), mockDataToSign(DEFAULT_MOCK_DATA_TO_SIGN), createDefaultMobileIdInformation());
                Assert.fail("Should not reach here");
            } catch (ClientException | IOException e) {
                Assert.assertEquals("Mobile-ID service error", e.getMessage());
            }
            WireMock.reset();
        });
    }

    @Test
    public void getStatus_midRestReturnsUnexpectedState() {
        stubGetStatusOkResponse("{\"state\":\"SOME_INVALID_STATE\"}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.INTERNAL_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndOk() {
        byte[] signatureBytes = "\tSignature bytes.\n".getBytes(StandardCharsets.UTF_8);
        stubGetStatusOkResponse("{" +
                " \"state\": \"COMPLETE\"," +
                " \"result\": \"OK\"," +
                " \"signature\": {" +
                "  \"value\": \"" + Base64.getEncoder().encodeToString(signatureBytes) + "\"," +
                "  \"algorithm\": \"SHA256WithECEncryption\"" +
                " }" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.SIGNATURE, response.getStatus());
        Assert.assertArrayEquals(signatureBytes, response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndTimeout() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"TIMEOUT\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.EXPIRED_TRANSACTION, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndNotMidClient() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"NOT_MID_CLIENT\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.NOT_MID_CLIENT, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndUserCancelled() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"USER_CANCELLED\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.USER_CANCEL, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndSignatureHashMismatch() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"SIGNATURE_HASH_MISMATCH\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.NOT_VALID, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndPhoneAbsent() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"PHONE_ABSENT\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.PHONE_ABSENT, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndDeliveryError() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"DELIVERY_ERROR\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.SENDING_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndSimError() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"SIM_ERROR\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.SENDING_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturnsCompleteAndUnexpectedResult() {
        stubGetStatusOkResponse("{" +
                "\"state\": \"COMPLETE\"," +
                "\"result\": \"SOME_INVALID_RESULT\"" +
                "}");

        MobileIdStatusResponse response = mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE);

        Assert.assertEquals(MobileIdSessionStatus.INTERNAL_ERROR, response.getStatus());
        Assert.assertNull(response.getSignature());
    }

    @Test
    public void getStatus_midRestReturns5XX() {
        Stream.of(HttpStatus.values()).filter(HttpStatus::is5xxServerError).forEach(status -> {
            stubGetStatusErrorResponse(status.value());
            try {
                Mockito.when(resource.getInputStream()).thenReturn(SmartIdApiClientTest.class.getClassLoader().getResource("mid_truststore.p12").openStream());
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            assertThrows(ServerErrorException.class, () -> mobileIdApiClient.getSignatureStatus(createRPInfo(), DEFAULT_MOCK_SESSION_CODE));
            WireMock.reset();
        });
    }

    private MobileIdInformation createDefaultMobileIdInformation() {
        return MobileIdInformation.builder()
                .personIdentifier(DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER)
                .phoneNo(DEFAULT_MOCK_PHONE_NUMBER)
                .language(DEFAULT_MOCK_LANGUAGE)
                .messageToDisplay(DEFAULT_MOCK_DISPLAY_TEXT)
                .build();
    }

    private RelyingPartyInfo createRPInfo() {
        return RelyingPartyInfo.builder()
                .name(DEFAULT_MOCK_RELYING_PARTY_NAME)
                .uuid(DEFAULT_MOCK_RELYING_PARTY_UUID.toString())
                .build();
    }

    private String createDefaultCertificateRequestJson() {
        return toJson(Map.of(
                "relyingPartyUUID", DEFAULT_MOCK_RELYING_PARTY_UUID,
                "relyingPartyName", DEFAULT_MOCK_RELYING_PARTY_NAME,
                "phoneNumber", DEFAULT_MOCK_PHONE_NUMBER,
                "nationalIdentityNumber", DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER
        ));
    }

    private String createDefaultSigningInitiationRequestJson() {
        return toJson(Map.of(
                "relyingPartyUUID", DEFAULT_MOCK_RELYING_PARTY_UUID,
                "relyingPartyName", DEFAULT_MOCK_RELYING_PARTY_NAME,
                "phoneNumber", DEFAULT_MOCK_PHONE_NUMBER,
                "nationalIdentityNumber", DEFAULT_MOCK_NATIONAL_IDENTITY_NUMBER,
                "hash", Base64.getEncoder().encodeToString(DigestUtils.sha256(DEFAULT_MOCK_DATA_TO_SIGN)),
                "hashType", "SHA256",
                "language", DEFAULT_MOCK_LANGUAGE,
                "displayText", DEFAULT_MOCK_DISPLAY_TEXT
        ));
    }

    private void stubCertificateRequestOkResponse(String responseBody) {
        WireMock.stubFor(
                WireMock.post("/certificate")
                        .withRequestBody(WireMock.equalToJson(createDefaultCertificateRequestJson()))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubCertificateRequestErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.post("/certificate")
                        .withRequestBody(WireMock.equalToJson(createDefaultCertificateRequestJson()))
                        .willReturn(WireMock.aResponse().withStatus(status))
        );
    }

    private void stubSigningInitiationOkResponse(String responseBody) {
        WireMock.stubFor(
                WireMock.post("/signature")
                        .withRequestBody(WireMock.equalToJson(createDefaultSigningInitiationRequestJson()))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubSigningInitiationErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.post("/signature")
                        .withRequestBody(WireMock.equalToJson(createDefaultSigningInitiationRequestJson()))
                        .willReturn(WireMock.aResponse().withStatus(status))
        );
    }

    private void stubGetStatusOkResponse(String responseBody) {
        WireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo(format("/signature/session/%s", DEFAULT_MOCK_SESSION_CODE)))
                        .withQueryParam("timeoutMs", WireMock.equalTo("30000"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody))
        );
    }

    private void stubGetStatusErrorResponse(int status) {
        WireMock.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/signature/session/" + DEFAULT_MOCK_SESSION_CODE))
                        .withQueryParam("timeoutMs", WireMock.equalTo("30000"))
                        .willReturn(WireMock.aResponse().withStatus(status))
        );
    }

    private DataToSign mockDataToSign(byte[] dataToBeSigned) {
        DataToSign dataToSignMock = Mockito.mock(DataToSign.class);
        Mockito.doReturn(dataToBeSigned).when(dataToSignMock).getDataToSign();
        Mockito.doReturn(DigestAlgorithm.SHA256).when(dataToSignMock).getDigestAlgorithm();
        return dataToSignMock;
    }

    private String toJson(Object request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create json from object", e);
        }
    }
}
