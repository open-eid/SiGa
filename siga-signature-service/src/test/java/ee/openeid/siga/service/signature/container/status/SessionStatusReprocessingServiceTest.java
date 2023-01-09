package ee.openeid.siga.service.signature.container.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.model.*;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.session.SessionStatus;
import ee.openeid.siga.common.session.SessionStatus.StatusError;
import ee.openeid.siga.common.session.SignatureSession;
import ee.openeid.siga.service.signature.configuration.SessionStatusReprocessingProperties;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.mobileid.InitMidSignatureResponse;
import ee.openeid.siga.service.signature.mobileid.MobileIdApiClient;
import ee.openeid.siga.service.signature.mobileid.MobileIdSessionStatus;
import ee.openeid.siga.service.signature.smartid.InitSmartIdSignatureResponse;
import ee.openeid.siga.service.signature.smartid.SmartIdApiClient;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestConfiguration;
import ee.openeid.siga.session.SessionService;
import ee.sk.smartid.SmartIdCertificate;
import lombok.SneakyThrows;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.http.Fault.*;
import static ee.openeid.siga.common.session.SessionStatus.ProcessingStatus.EXCEPTION;
import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "datafileContainer"})
@SpringBootTest(classes = {TestConfiguration.class}, webEnvironment = RANDOM_PORT, properties = {
        "siga.security.jasypt.encryption-algo=PBEWITHSHA-256AND256BITAES-CBC-BC",
        "siga.security.jasypt.encryption-key=encryptorKey",
        "siga.security.hmac.expiration=-1",
        "siga.security.hmac.clock-skew=2",
        "siga.dd4j.configuration-location=digidoc4j.yaml",
        "siga.dd4j.tsl-refresh-job-cron=0 0 3 * * *",
        "siga.ignite.application-cache-version=v1",
        "siga.ignite.configuration-location=classpath:ignite-test-configuration.xml",
        "siga.midrest.url=https://localhost:9090/mid-api",
        "siga.midrest.truststore-path=classpath:mid_truststore.p12",
        "siga.midrest.truststore-password=changeIt",
        "siga.midrest.status-polling-delay=0",
        "siga.sid.url=https://localhost:9090/sid-api",
        "siga.sid.truststore-path=classpath:sid_truststore.p12",
        "siga.sid.truststore-password=changeIt",
        "siga.sid.status-polling-delay=0",
        "siga.status-reprocessing.fixed-rate=1000",
        "siga.status-reprocessing.initial-delay=0",
        "siga.status-reprocessing.exception-timeout=0",
        "siga.status-reprocessing.max-processing-attempts=3",
})
public class SessionStatusReprocessingServiceTest {
    private static final WireMockServer mockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .httpDisabled(true)
            .httpsPort(9090)
            .keystorePath("src/test/resources/mock_keystore.p12")
            .keystorePassword("changeit")
            .keyManagerPassword("changeit")
            .notifier(new ConsoleNotifier(true))
    );
    private static final String MOCK_SESSION_CODE = "mock-session-code";
    private static final String SID_DOCUMENT_NUMBER = "PNOEE-123456789-QWER";
    private static final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ESTEID2018.p12", "1234".toCharArray());
    @SpyBean
    private MobileIdApiClient mobileIdApiClient;
    @SpyBean
    private SmartIdApiClient smartIdApiClient;
    @SpyBean
    private SessionStatusReprocessingService sessionStatusReprocessingService;
    @SpyBean
    private HashcodeContainerSigningService hashcodeContainerSigningService;
    @MockBean
    private SecurityContext securityContext;
    @MockBean
    private Authentication authentication;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private SessionStatusReprocessingProperties reprocessingProperties;

    @BeforeClass
    public static void startMocks() {
        mockServer.start();
    }

    @Before
    public void setUp() {
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f")
                .build());
        SecurityContextHolder.setContext(securityContext);
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        Mockito.when(SecurityContextHolder.getContext().getAuthentication().getName()).thenReturn("user_name");

        InitMidSignatureResponse midResponse = new InitMidSignatureResponse();
        midResponse.setSessionCode(MOCK_SESSION_CODE);
        midResponse.setChallengeId("1234");
        Mockito.doReturn(pkcs12Esteid2018SignatureToken.getCertificate()).when(mobileIdApiClient).getCertificate(any(), any());
        Mockito.doReturn(midResponse).when(mobileIdApiClient).initMobileSigning(any(), any(), any());

        InitSmartIdSignatureResponse sidResponse = new InitSmartIdSignatureResponse();
        sidResponse.setSessionCode(MOCK_SESSION_CODE);
        sidResponse.setChallengeId("1234");
        SmartIdCertificate sidCertificateResponse = new SmartIdCertificate();
        sidCertificateResponse.setCertificate(pkcs12Esteid2018SignatureToken.getCertificate());
        sidCertificateResponse.setDocumentNumber(SID_DOCUMENT_NUMBER);
        sidCertificateResponse.setCertificateLevel("QUALIFIED");
        Mockito.doReturn(sidCertificateResponse).when(smartIdApiClient).getCertificate(any(), any());
        Mockito.doReturn(sidResponse).when(smartIdApiClient).initSmartIdSigning(any(), any(), any());
    }

    @After
    public void removeTestContainer() {
        sessionService.removeBySessionId(CONTAINER_SESSION_ID);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMaxPollingAttempts_NoMidReprocessing() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withStatus(500)));
        SigningChallenge signingChallenge = startMobileIdSigningAndAssertPollingException(containerSessionId, "HTTP 500 Server Error");

        assertMaxReprocessingAttempts(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMobileIdApiHttp500_ReprocessMidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withStatus(500)));
        SigningChallenge signingChallenge = startMobileIdSigningAndAssertPollingException(containerSessionId, "HTTP 500 Server Error");

        assertReprocessMidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMobileIdApiConnectResetByPeer_ReprocessMidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));
        SigningChallenge signingChallenge = startMobileIdSigningAndAssertPollingException(containerSessionId, "Connection reset");

        assertReprocessMidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMobileIdApiMalformedResponse_ReprocessMidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(MALFORMED_RESPONSE_CHUNK)));
        SigningChallenge signingChallenge = startMobileIdSigningAndAssertPollingException(containerSessionId, "java.io.IOException: Premature EOF");

        assertReprocessMidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMobileIdApiReturnsInvalidData_ReprocessMidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(RANDOM_DATA_THEN_CLOSE)));
        SigningChallenge signingChallenge = startMobileIdSigningAndAssertPollingException(containerSessionId, "Failed to convert a response into an exception.");

        assertReprocessMidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenMaxPollingAttempts_NoSidReprocessing() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withStatus(500)));
        SigningChallenge signingChallenge = startSmartIdSigningAndAssertPollingException(containerSessionId, "HTTP 500 Server Error");

        assertMaxReprocessingAttempts(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenSmartIdApiHttp500_ReprocessSidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withStatus(500)));
        SigningChallenge signingChallenge = startSmartIdSigningAndAssertPollingException(containerSessionId, "HTTP 500 Server Error");

        assertReprocessSidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenSmartIdApiConnectResetByPeer_ReprocessSidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(CONNECTION_RESET_BY_PEER)));
        SigningChallenge signingChallenge = startSmartIdSigningAndAssertPollingException(containerSessionId, "Connection reset");

        assertReprocessSidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenSmartIdApiMalformedResponse_ReprocessSidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(MALFORMED_RESPONSE_CHUNK)));
        SigningChallenge signingChallenge = startSmartIdSigningAndAssertPollingException(containerSessionId, "java.io.IOException: Premature EOF");

        assertReprocessSidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    @Test
    public void processFailedSignatureStatusRequests_WhenSmartIdApiReturnsInvalidData_ReprocessSidStatusRequest() {
        String containerSessionId = crateContainerSession();
        mockServer.stubFor(WireMock.any(urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                .willReturn(aResponse().withFault(RANDOM_DATA_THEN_CLOSE)));
        SigningChallenge signingChallenge = startSmartIdSigningAndAssertPollingException(containerSessionId, "Failed to convert a response into an exception.");

        assertReprocessSidSignatureStatusRequest(containerSessionId, signingChallenge);
    }

    private SigningChallenge startMobileIdSigningAndAssertPollingException(String containerSessionId, String expectedErrorMessage) {
        String containerId = containerSessionId.substring(13, 49);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        MobileIdInformation mobileIdInformation = RequestUtil.createMobileInformation();
        SigningChallenge signingChallenge = hashcodeContainerSigningService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);
        assertThat(signingChallenge, notNullValue());
        assertThat(signingChallenge.getChallengeId(), equalTo("1234"));

        await().atMost(60, SECONDS).untilAsserted(() -> {
            Session containerSession = sessionService.getContainerBySessionId(containerSessionId);
            SignatureSession signatureSession = containerSession.getSignatureSession(signingChallenge.getGeneratedSignatureId());
            assertThat(signatureSession, notNullValue());
            assertThat(signatureSession.getSigningType(), equalTo(SigningType.MOBILE_ID));
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            assertThat(sessionStatus.getProcessingStatus(), equalTo(EXCEPTION));
            StatusError statusError = sessionStatus.getStatusError();
            assertThat(statusError.getErrorCode(), equalTo("INTERNAL_SERVER_ERROR"));
            assertThat(statusError.getErrorMessage(), containsString(expectedErrorMessage));
        });
        return signingChallenge;
    }

    private SigningChallenge startSmartIdSigningAndAssertPollingException(String containerSessionId, String expectedErrorMessage) {
        String containerId = containerSessionId.substring(13, 49);
        SignatureParameters signatureParameters = createSignatureParameters(pkcs12Esteid2018SignatureToken.getCertificate());
        SmartIdInformation smartIdInformation = RequestUtil.createSmartIdInformation();
        SigningChallenge signingChallenge = hashcodeContainerSigningService.startSmartIdSigning(containerId, smartIdInformation, signatureParameters);
        assertThat(signingChallenge, notNullValue());
        assertThat(signingChallenge.getChallengeId(), equalTo("1234"));

        await().atMost(60, SECONDS).untilAsserted(() -> {
            Session containerSession = sessionService.getContainerBySessionId(containerSessionId);
            SignatureSession signatureSession = containerSession.getSignatureSession(signingChallenge.getGeneratedSignatureId());
            assertThat(signatureSession, notNullValue());
            assertThat(signatureSession.getSigningType(), equalTo(SigningType.SMART_ID));
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            assertThat(sessionStatus.getProcessingStatus(), equalTo(EXCEPTION));
            StatusError statusError = sessionStatus.getStatusError();
            assertThat(statusError.getErrorCode(), equalTo("INTERNAL_SERVER_ERROR"));
            assertThat(statusError.getErrorMessage(), containsString(expectedErrorMessage));
        });
        return signingChallenge;
    }

    private void assertReprocessMidSignatureStatusRequest(String containerSessionId, SigningChallenge signingChallenge) {
        mockSuccessfulMidSignatureStatusResponse(containerSessionId, signingChallenge);
        await().atMost(60, SECONDS).untilAsserted(() ->
                Mockito.verify(sessionStatusReprocessingService).processFailedContainerSession(any(), eq(containerSessionId)));
        await().atMost(60, SECONDS).untilAsserted(() -> {
            HashcodeContainerSession containerSession = (HashcodeContainerSession) sessionService.getContainerBySessionId(containerSessionId);
            List<HashcodeSignatureWrapper> signatures = containerSession.getSignatures();
            assertThat(signatures, hasSize(0));
            Map<String, SignatureSession> signatureSessions = containerSession.getSignatureSessions();
            assertThat(signatureSessions.values(), hasSize(1));
            SignatureSession signatureSession = signatureSessions.values().iterator().next();
            assertThat(signatureSession.getSessionStatus().getStatus(), equalTo(MobileIdSessionStatus.SIGNATURE.name()));
            assertThat(signatureSession.getSignature(), notNullValue());
        });
    }

    private void assertReprocessSidSignatureStatusRequest(String containerSessionId, SigningChallenge signingChallenge) {
        mockSuccessfulSidSignatureStatusResponse(containerSessionId, signingChallenge);
        await().atMost(60, SECONDS).untilAsserted(() ->
                Mockito.verify(sessionStatusReprocessingService).processFailedContainerSession(any(), eq(containerSessionId)));
        await().atMost(60, SECONDS).untilAsserted(() -> {
            HashcodeContainerSession containerSession = (HashcodeContainerSession) sessionService.getContainerBySessionId(containerSessionId);
            List<HashcodeSignatureWrapper> signatures = containerSession.getSignatures();
            assertThat(signatures, hasSize(0));
            Map<String, SignatureSession> signatureSessions = containerSession.getSignatureSessions();
            assertThat(signatureSessions.values(), hasSize(1));
            SignatureSession signatureSession = signatureSessions.values().iterator().next();
            assertThat(signatureSession.getSessionStatus().getStatus(), equalTo(MobileIdSessionStatus.SIGNATURE.name()));
            assertThat(signatureSession.getSignature(), notNullValue());
        });
    }

    private void mockSuccessfulMidSignatureStatusResponse(String containerSessionId, SigningChallenge signingChallenge) {
        Session containerSession = sessionService.getContainerBySessionId(containerSessionId);
        SignatureSession signatureSession = containerSession.getSignatureSession(signingChallenge.getGeneratedSignatureId());
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, signatureSession.getDataToSign().getDataToSign());
        String responseBody = "{" +
                " \"state\": \"COMPLETE\"," +
                " \"result\": \"OK\"," +
                " \"signature\": {" +
                "  \"value\": \"" + Base64.getEncoder().encodeToString(signatureRaw) + "\"," +
                "  \"algorithm\": \"SHA256WithECEncryption\"" +
                " }" +
                "}";
        mockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo(format("/mid-api/signature/session/%s", MOCK_SESSION_CODE)))
                        .withQueryParam("timeoutMs", WireMock.equalTo("30000"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody)));
    }

    @SneakyThrows
    private void mockSuccessfulSidSignatureStatusResponse(String containerSessionId, SigningChallenge signingChallenge) {
        Session containerSession = sessionService.getContainerBySessionId(containerSessionId);
        SignatureSession signatureSession = containerSession.getSignatureSession(signingChallenge.getGeneratedSignatureId());
        byte[] signatureRaw = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.SHA512, signatureSession.getDataToSign().getDataToSign());
        String responseBody = "{\n" +
                "    \"state\": \"COMPLETE\",\n" +
                "    \"result\": {\n" +
                "            \"endResult\": \"OK\",\n" +
                "            \"documentNumber\": \"" + SID_DOCUMENT_NUMBER + "\"\n" +
                "    },\n" +
                "    \"signature\": {\n" +
                "        \"value\": \"" + Base64.getEncoder().encodeToString(signatureRaw) + "\",\n" +
                "        \"algorithm\": \"sha512WithRSAEncryption\"\n" +
                "    },\n" +
                "    \"cert\": {\n" +
                "        \"value\": \"" + new String(org.bouncycastle.util.encoders.Base64.encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())) + "\",\n" +
                "        \"assuranceLevel\": \"http://eidas.europa.eu/LoA/substantial\",\n" +
                "        \"certificateLevel\": \"ADVANCED\"\n" +
                "    }\n" +
                "}";
        mockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo(format("/sid-api/session/%s", MOCK_SESSION_CODE)))
                        .withQueryParam("timeoutMs", WireMock.equalTo("30000"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody)));
    }

    private void assertMaxReprocessingAttempts(String containerSessionId, SigningChallenge signingChallenge) {
        await().atMost(60, SECONDS).untilAsserted(() -> {
            HashcodeContainerSession containerSession = (HashcodeContainerSession) sessionService.getContainerBySessionId(containerSessionId);
            List<HashcodeSignatureWrapper> signatures = containerSession.getSignatures();
            assertThat(signatures, hasSize(0));
            Map<String, SignatureSession> signatureSessions = containerSession.getSignatureSessions();
            assertThat(signatureSessions.values(), hasSize(1));
            SignatureSession signatureSession = signatureSessions.values().iterator().next();
            SessionStatus sessionStatus = signatureSession.getSessionStatus();
            StatusError statusError = sessionStatus.getStatusError();
            assertThat(statusError, notNullValue());
            assertThat(statusError.getErrorCode(), equalTo("INTERNAL_SERVER_ERROR"));
            assertThat(signatureSession.getSignature(), nullValue());
            assertThat(sessionStatus.getProcessingCounter(), equalTo(reprocessingProperties.getMaxProcessingAttempts()));
        });

        Mockito.clearInvocations(sessionStatusReprocessingService);
        await().atMost(60, SECONDS).untilAsserted(() ->
                Mockito.verify(sessionStatusReprocessingService, Mockito.times(1)).processFailedStatusRequests());
        Mockito.clearInvocations(sessionStatusReprocessingService);
        await().atMost(60, SECONDS).untilAsserted(() ->
                Mockito.verify(sessionStatusReprocessingService, Mockito.atLeastOnce()).processFailedStatusRequests());
        Mockito.verify(sessionStatusReprocessingService, Mockito.never()).processFailedSignatureSession(any(), eq(signingChallenge.getGeneratedSignatureId()), any(), any());
    }

    private String crateContainerSession() {
        String containerSessionId = "v1_user_name_" + UUID.randomUUID();
        sessionService.update(HashcodeContainerSession.builder()
                .sessionId(containerSessionId)
                .clientName(CLIENT_NAME)
                .serviceName(SERVICE_NAME)
                .serviceUuid(SERVICE_UUID)
                .dataFiles(createHashcodeDataFileListWithOneFile()).build());
        return containerSessionId;
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
}
