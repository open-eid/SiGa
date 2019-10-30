package ee.openeid.siga.service.signature.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.HashcodeSignatureWrapper;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.service.signature.configuration.SivaClientConfigurationProperties;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class SivaClientTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);
    @Mock
    private SivaClientConfigurationProperties sivaConfigurationProperties;
    private SivaClient sivaClient;
    private String requestUrl;

    @Before
    public void setUp() {
        requestUrl = "http://localhost:" + wireMockRule.port();
        when(sivaConfigurationProperties.getUrl()).thenReturn(requestUrl);
        sivaClient = new SivaClient();
        sivaClient.setRestTemplate(new RestTemplate());
        sivaClient.setConfigurationProperties(sivaConfigurationProperties);
    }

    @After
    public void tearDown() {
        WireMock.reset();
    }

    @Test
    public void successfulSivaResponse() throws Exception {
        String body = toJson(RequestUtil.createValidationResponse());
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body))
        );
        ValidationConclusion response = sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
        Assert.assertEquals(Integer.valueOf(1), response.getSignaturesCount());
        Assert.assertEquals(Integer.valueOf(1), response.getValidSignaturesCount());
    }

    @Test
    public void siva404NotFound() throws Exception {
        exceptionRule.expect(TechnicalException.class);
        exceptionRule.expectMessage("Unable to get valid response from client");
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404))
        );
        sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
    }

    @Test
    public void siva500InternalServerError() throws Exception {
        exceptionRule.expect(TechnicalException.class);
        exceptionRule.expectMessage("Unable to get valid response from client");
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500))
        );
        sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
    }

    @Test
    public void hashMismatch() throws Exception {
        exceptionRule.expect(InvalidHashAlgorithmException.class);
        exceptionRule.expectMessage("Container contains invalid hash algorithms");

        List<HashcodeSignatureWrapper> signatureWrappers = RequestUtil.createSignatureWrapper();
        signatureWrappers.get(0).getDataFiles().get(0).setHashAlgo("SHA386");
        sivaClient.validateHashcodeContainer(signatureWrappers, RequestUtil.createHashcodeDataFiles());
    }

    @Test
    public void sivaDocumentMalformed() throws Exception {
        exceptionRule.expect(InvalidContainerException.class);
        exceptionRule.expectMessage("Document malformed");
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
        sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
    }

    @Test
    public void sivaSignatureMalformed() throws Exception {
        exceptionRule.expect(InvalidSignatureException.class);
        exceptionRule.expectMessage("Signature malformed");
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
        sivaClient.validateHashcodeContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFileListWithOneFile());
    }

    private String toJson(Object request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create json from object", e);
        }
    }


}
