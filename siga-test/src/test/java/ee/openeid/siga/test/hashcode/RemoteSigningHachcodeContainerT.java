package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.common.Result;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RemoteSigningHachcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signContainerRemotely() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    public void signContainerRemotelyWithMultipleSignatures() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());

        response.then().statusCode(200).body("result", equalTo(Result.OK.name()));

        response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse2.getDataToSign(), dataToSignResponse2.getDigestAlgorithm())), dataToSignResponse2.getGeneratedSignatureId());

        response.then().statusCode(200).body("result", equalTo(Result.OK.name()));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(3))
                .body("validationConclusion.signaturesCount", equalTo(3));
    }

    @Test
    public void startRemoteSigningHashcodeContainerReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", null, null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_PEM);
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptySigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, ""));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("-&32%", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfileFormat() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "123"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "B_BES"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerReturnsOk() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        JSONObject request = new JSONObject();
        Response response = putRemoteSigningInSession(flow, request, dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptySignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(""), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithInvalidSignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_SIGNATURE);
    }

    @Test
    public void getRemoteSigningHashcodeContainer() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToRemoteSigningHashcodeContainer() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = head(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        response.then()
                .statusCode(405);
    }

    @Test
    public void optionsToRemoteSigningHashcodeContainer() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = options(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToRemoteSigningHashcodeContainer() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = patch(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
