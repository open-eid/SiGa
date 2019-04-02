package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeRemoteSigningSignatureValueRequest;
import static ee.openeid.siga.test.utils.digestSigner.signDigest;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeleteHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void uploadHashcodeContainerAndDelete() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void createHashcodeContainerAndDelete() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAlwaysReturnsOk() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteHashcodeContainer(flow);

        Response response = deleteHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo("OK"));
    }

    @Test
    public void deleteHashcodeContainerBeforeSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingIt() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));
        getHashcodeContainer(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerBeforeFinishingMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeMidSigningInSession(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerDuringMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        getHashcodeMidSigningInSession(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeMidSigningInSession(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        pollForMidSigning(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterValidation() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        deleteHashcodeContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getHashcodeSignatureList(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerForOtherClientNotPossible() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        deleteHashcodeContainer(flow);

        flow.setServiceUuid(SERVICE_UUID_1);
        flow.setServiceSecret(SERVICE_SECRET_1);

        Response response = getHashcodeSignatureList(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("signatures[0].signerInfo"), equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void postToDeleteHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, IOException, JSONException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = post(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow, "");
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }
}
