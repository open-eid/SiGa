package ee.openeid.siga.service.signature.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.service.signature.DetachedDataFileContainerService;
import ee.openeid.siga.service.signature.configuration.SivaConfigurationProperties;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class SivaClientTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(Options.DYNAMIC_PORT);
    @Mock
    private SivaConfigurationProperties sivaConfigurationProperties;
    @Mock
    private DetachedDataFileContainerService detachedDataFileContainerService;
    private SivaClient sivaClient;
    private String requestUrl;

    @Before
    public void setUp() {
        requestUrl = "http://localhost:" + wireMockRule.port();
        when(sivaConfigurationProperties.getUrl()).thenReturn(requestUrl);
        sivaClient = new SivaClient();
        sivaClient.setRestTemplate(new RestTemplate());
        sivaClient.setConfigurationProperties(sivaConfigurationProperties);
        sivaClient.setDetachedDataFileContainerService(detachedDataFileContainerService);
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
        ValidationConclusion response = sivaClient.validateDetachedDataFileContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFiles());
        Assert.assertEquals(Integer.valueOf(1), response.getSignaturesCount());
        Assert.assertEquals(Integer.valueOf(1), response.getValidSignaturesCount());
    }

    @Test
    public void siva404NotFound() throws Exception {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Unable to get valid response from client");
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404))
        );
        sivaClient.validateDetachedDataFileContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFiles());
    }

    @Test
    public void siva500InternalServerError() throws Exception {
        exceptionRule.expect(ClientException.class);
        exceptionRule.expectMessage("Unable to get valid response from client");
        WireMock.stubFor(
                WireMock.post("/validateHashcode").willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(500))
        );
        sivaClient.validateDetachedDataFileContainer(RequestUtil.createSignatureWrapper(), RequestUtil.createHashcodeDataFiles());
    }

    @Test
    public void hashMismatch() throws Exception {
        exceptionRule.expect(InvalidHashAlgorithmException.class);
        exceptionRule.expectMessage("Container contains invalid hash algorithms");

        SignatureWrapper signatureWrapper = RequestUtil.createSignatureWrapper();
        signatureWrapper.getDataFiles().get(0).setHashAlgo("SHA386");
        sivaClient.validateDetachedDataFileContainer(signatureWrapper, RequestUtil.createHashcodeDataFiles());
    }

    protected String toJson(Object request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create json from object", e);
        }
    }


}
