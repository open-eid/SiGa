package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.smartIdSigningRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;

public class SmartIdSigningAsicContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }


    @Ignore("Test TSL needs to be updated")
    @Test
    public void signWithSmartIdSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("10101010005", "LT_TM"));
        String signatureId = response.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
