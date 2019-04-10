package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeContainerSystemT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createNewHashcodeContainerAndSignWithMid() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        pollForMidSigning(flow);
        Response containerResponse = getHashcodeContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteHashcodeContainer(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void uploadHashcodeContainerAndSignRemotly() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path("dataToSign"), dataToSignResponse.getBody().path("digestAlgorithm"))));

        Response containerResponse = getHashcodeContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void createHashcodeContainerSignRemotlyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path("dataToSign"), dataToSignResponse.getBody().path("digestAlgorithm"))));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));

        deleteHashcodeContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void getSignaturesShouldReturnListOfSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = getHashcodeSignatureList(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("signatures[0].id"), equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"));
    }




}
