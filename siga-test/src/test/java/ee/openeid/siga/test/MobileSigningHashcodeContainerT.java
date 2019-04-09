package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeMidSigningRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class MobileSigningHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void signWithMidSuccessfully() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        pollForMidSigning(flow);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Ignore //TODO: OCSP is not fetched for some reason, needs investigation
    @Test
    public void signWithLtMidSuccessfully() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("50001018865", "+37060000666"));
        pollForMidSigning(flow);

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void mobileIdNotActivated() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019928", "+37200000366"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(CLIENT_EXCEPTION));
    }

    @Test
    public void mobileIdCertificateRevoked() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019939", "+37200000266"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(CLIENT_EXCEPTION));
    }

    @Test
    public void mobileIdSendingFailed() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019947", "+37207110066"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(SENDING_ERROR));
    }

    @Test
    public void mobileIdUserCancel() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019950", "+37201100266"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(USER_CANCEL));
    }

    @Test
    public void mobileIdSignatureNotValid() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019961", "+37200000666"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(NOT_VALID));
    }

    @Test
    public void mobileIdSimError() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019972", "+37201200266"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(SIM_ERROR));
    }

    @Test
    public void mobileIdPhoneNotInNetwork() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019983", "+37213100266"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(PHONE_ABSENT));
    }

    @Test
    public void mobileIdUserTimeout() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("50001018908", "+37066000266"));

        Response response = pollForMidSigning(flow);

        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(EXPIRED_TRANSACTION));
    }
}
