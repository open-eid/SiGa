package ee.openeid.siga.mobileid.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.MidException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.mobileid.model.mid.GetMobileSignHashStatusResponse;
import ee.openeid.siga.mobileid.model.mid.MobileSignHashResponse;
import ee.openeid.siga.mobileid.model.mid.ProcessStatusType;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class MobileIdServiceTest {

    private static final String FAULT_STRING_PLACEHOLDER = "${fault_string}";
    private static final String DETAIL_MESSAGE_PLACEHOLDER = "${detail_message}";
    private static final String DEFAULT_MOCK_HASH_TYPE = "SHA256";
    private static final String DEFAULT_MOCK_HASH = "hash";


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);

    private MobileIdService mobileIdService;

    @Before
    public void setUp() {
        String serviceUrl = "http://localhost:" + wireMockRule.port();
        mobileIdService = new MobileIdService(serviceUrl);
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }


    @Test
    public void initMobileSignHash_midReturnsOkResponse() {
        stubMobileIdServiceResponse(200, loadResourceAsString("/wiremock/mobile-sign-ok-response.xml"));

        MobileSignHashResponse response = mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        Assert.assertEquals("session-code", response.getSesscode());
        Assert.assertEquals("1234", response.getChallengeID());
        Assert.assertEquals("OK", response.getStatus());
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault300() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "300")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error related to user's mobile phone"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("GENERAL_ERROR", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault301() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "301")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Not a Mobile-ID user"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("NOT_FOUND", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault302() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "302")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "The certificate of the user is not valid (OCSP said: REVOKED)"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault303() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "303")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is not activated or/and status of the certificate is unknown (OCSP said: UNKNOWN)"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault304() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "304")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is suspended"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsSoapFault305() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "305")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is expired"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsUnhandledSoapFault() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "200")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error of the service"));
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service error", e.getMessage());
        }
    }

    @Test
    public void initMobileSignHash_midReturnsUnhandledError() {
        stubMobileIdServiceResponse(400, "");
        try {
            mobileIdService.initMobileSignHash(createDefaultMobileIdInformation(), DEFAULT_MOCK_HASH_TYPE, DEFAULT_MOCK_HASH);
        } catch (TechnicalException e) {
            Assert.assertEquals("Unable to receive valid response from DigiDocService", e.getMessage());
        }
    }


    @Test
    public void getMobileSignHashStatus_midReturnsOkResponse() {
        stubMobileIdServiceResponse(200, loadResourceAsString("/wiremock/mobile-sign-status-ok-response.xml"));

        GetMobileSignHashStatusResponse response = mobileIdService.getMobileSignHashStatus("session-code");
        Assert.assertEquals(ProcessStatusType.SIGNATURE, response.getStatus());
        Assert.assertArrayEquals("signature".getBytes(StandardCharsets.UTF_8), response.getSignature());
        Assert.assertEquals("session-code", response.getSesscode());
        Assert.assertArrayEquals("certificate-data".getBytes(StandardCharsets.UTF_8), response.getCertificateData());
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault300() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "300")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error related to user's mobile phone"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("GENERAL_ERROR", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault301() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "301")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Not a Mobile-ID user"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("NOT_FOUND", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault302() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "302")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "The certificate of the user is not valid (OCSP said: REVOKED)"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault303() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "303")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is not activated or/and status of the certificate is unknown (OCSP said: UNKNOWN)"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault304() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "304")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is suspended"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsSoapFault305() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "305")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is expired"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsUnhandledSoapFault() {
        stubMobileIdServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "200")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error of the service"));
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service error", e.getMessage());
        }
    }

    @Test
    public void getMobileSignHashStatus_midReturnsUnhandledError() {
        stubMobileIdServiceResponse(400, "");
        try {
            mobileIdService.getMobileSignHashStatus("session-code");
        } catch (TechnicalException e) {
            Assert.assertEquals("Unable to receive valid response from DigiDocService", e.getMessage());
        }
    }


    private MobileIdInformation createDefaultMobileIdInformation() {
        return MobileIdInformation.builder()
                .relyingPartyUUID("relying-party-UUID")
                .relyingPartyName("relying-party-name")
                .language("ENG")
                .build();
    }

    private void stubMobileIdServiceResponse(int status, String responseBody) {
        WireMock.stubFor(
                WireMock.post("/")
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "text/xml")
                                .withBody(responseBody))
        );
    }


    private String loadResourceAsString(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource", e);
        }
    }

}
