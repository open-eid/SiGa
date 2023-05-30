package ee.openeid.siga.service.signature.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@WireMockTest
class SivaClientTest {

    @InjectMocks
    private SivaClient sivaClient;
    @Spy
    private RestTemplate restTemplate = new RestTemplate();
    @Mock
    private SivaClientConfigurationProperties sivaConfigurationProperties;
    private String requestUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMockServer) {
        requestUrl = "http://localhost:" + wireMockServer.getHttpPort();
        lenient().when(sivaConfigurationProperties.getUrl()).thenReturn(requestUrl);
    }

    @AfterEach
    void tearDown() {
        WireMock.reset();
    }

    @Test
    void successfulSivaResponse() throws Exception {
        String body = toJson(RequestUtil.createValidationResponse());
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body))
        );
        ValidationConclusion response = sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
        assertEquals(Integer.valueOf(1), response.getSignaturesCount());
        assertEquals(Integer.valueOf(1), response.getValidSignaturesCount());
    }

    @Test
    void invalidSivaTruststoreCertificate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(), any(), any(Class.class))).thenThrow(new ResourceAccessException("I/O error on POST request for https://siva-arendus.eesti.ee/V3/validateHashcode"));
        sivaClient = new SivaClient(restTemplate, sivaConfigurationProperties);

        TechnicalException caughtException = assertThrows(
            TechnicalException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("SIVA service error", caughtException.getMessage());
    }

    @Test
    void siva404NotFound() {
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404))
        );

        TechnicalException caughtException = assertThrows(
            TechnicalException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Unable to get valid response from client", caughtException.getMessage());
    }

    @Test
    void siva500InternalServerError() {
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500))
        );

        TechnicalException caughtException = assertThrows(
            TechnicalException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Unable to get valid response from client", caughtException.getMessage());
    }

    @Test
    void hashMismatch() throws IOException, URISyntaxException {
        List<HashcodeSignatureWrapper> signatureWrappers = RequestUtil.createSignatureWrapper();
        signatureWrappers.get(0).getDataFiles().get(0).setHashAlgo("SHA386");

        InvalidHashAlgorithmException caughtException = assertThrows(
            InvalidHashAlgorithmException.class, () -> sivaClient.validateHashcodeContainer(signatureWrappers, RequestUtil.createHashcodeDataFiles())
        );
        assertEquals("Container contains invalid hash algorithms", caughtException.getMessage());
    }

    @Test
    void sivaDocumentMalformed() {
        String body = "{\"requestErrors\": [{\n" +
                "    \"message\": \"Document malformed or not matching documentType\",\n" +
                "    \"key\": \"document\"\n" +
                "}]}";
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(400)
                        .withBody(body))
        );

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Document malformed", caughtException.getMessage());
    }

    @Test
    void sivaSignatureMalformed() {
        String body = "{\"requestErrors\": [{\n" +
                "    \"message\": \" Signature file malformed\",\n" +
                "    \"key\": \"signatureFiles.signature\"\n" +
                "}]}";
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(400)
                        .withBody(body))
        );

        InvalidSignatureException caughtException = assertThrows(
            InvalidSignatureException.class, () -> sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Signature malformed", caughtException.getMessage());
    }

    private String toJson(Object request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create json from object", e);
        }
    }


}
