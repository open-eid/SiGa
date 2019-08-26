package ee.openeid.siga.mobileid.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.MidException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.mobileid.model.dds.GetMobileCertificateResponse;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RunWith(MockitoJUnitRunner.class)
public class DigiDocServiceTest {

    private static final String DEFAULT_MOCK_ID_CODE = "id-code";
    private static final String DEFAULT_MOCK_PHONE_NO = "+3721234567";
    private static final String FAULT_STRING_PLACEHOLDER = "${fault_string}";
    private static final String DETAIL_MESSAGE_PLACEHOLDER = "${detail_message}";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);

    private DigiDocService digiDocService;

    @Before
    public void setUp() {
        String serviceUrl = "http://localhost:" + wireMockRule.port();
        digiDocService = new DigiDocService(serviceUrl);
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }


    @Test
    public void getMobileCertificate_ddsReturnsOkResponse() {
        stubDigiDocServiceResponse(200, loadResourceAsString("/wiremock/dds-ok-response.xml"));

        GetMobileCertificateResponse response = digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        Assert.assertEquals("OK", response.getSignCertStatus());
        Assert.assertEquals("certificate-data", response.getSignCertData());
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault300() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "300")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error related to user's mobile phone"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("GENERAL_ERROR", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault301() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "301")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Not a Mobile-ID user"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("NOT_FOUND", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault302() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "302")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "The certificate of the user is not valid (OCSP said: REVOKED)"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault303() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "303")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is not activated or/and status of the certificate is unknown (OCSP said: UNKNOWN)"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault304() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "304")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is suspended"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsSoapFault305() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "305")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "Certificate is expired"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (MidException e) {
            Assert.assertEquals("NOT_ACTIVE", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsUnhandledSoapFault() {
        stubDigiDocServiceResponse(500, loadResourceAsString("/wiremock/soap-fault-response.xml")
                .replace(FAULT_STRING_PLACEHOLDER, "200")
                .replace(DETAIL_MESSAGE_PLACEHOLDER, "General error of the service"));
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (ClientException e) {
            Assert.assertEquals("Mobile-ID service error", e.getMessage());
        }
    }

    @Test
    public void getMobileCertificate_ddsReturnsUnhandledError() {
        stubDigiDocServiceResponse(400, "");
        try {
            digiDocService.getMobileCertificate(DEFAULT_MOCK_ID_CODE, DEFAULT_MOCK_PHONE_NO);
        } catch (TechnicalException e) {
            Assert.assertEquals("Unable to receive valid response from DigiDocService", e.getMessage());
        }
    }


    private void stubDigiDocServiceResponse(int status, String responseBody) {
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