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
import static ee.openeid.siga.test.utils.digestSigner.signDigest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeContainerSystemT extends TestBase{

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void createNewHashcodeContainerAndSignWithMid() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
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
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
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
    public void validateHascodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest("hashcode.asice"));
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }


    @Test
    public void startRemoteSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void finalizeSignature() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        Response resp = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(resp.getBody().path("dataToSign"), resp.getBody().path("digestAlgorithm"))));
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("result"), equalTo("OK"));
    }

    @Test
    public void signWithMid() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        Response response = pollForMidSigning(flow);
        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void getSignaturesShouldReturnListOfSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        Response response = getHashcodeSignatureList(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("signatures[0].id"), equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"));
    }

    @Test
    public void getSignedContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        Response resp = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(resp.getBody().path("dataToSign"), resp.getBody().path("digestAlgorithm"))));
        Response response = getHashcodeContainer(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("container").toString().length(), notNullValue());
    }


}
