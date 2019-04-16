package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.SIGNER_CERT_PEM;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static org.hamcrest.CoreMatchers.equalTo;

public class SignatureCorrectnessT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Ignore //TODO: SIGARIA-63
    @Test
    public void signatureProfileTest() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT_TM"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path("dataToSign"), dataToSignResponse.getBody().path("digestAlgorithm"))));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        pollForMidSigning(flow);

        getHashcodeContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(3))
                .body("validationConclusion.signaturesCount", equalTo(3));
    }
}
