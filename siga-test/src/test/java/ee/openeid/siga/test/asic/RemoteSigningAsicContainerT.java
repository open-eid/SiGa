package ee.openeid.siga.test.asic;

import ee.openeid.siga.common.Result;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
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

public class RemoteSigningAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signContainerRemotely() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    public void signContainerRemotelyWithMultipleSignatures() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        CreateContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

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
    public void startRemoteSigningContainerReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", null, null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningContainerEmptyBody() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerMissingSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerMissingProfile() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_PEM);
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerEmptySigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerEmptyProfile() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, ""));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerInvalidSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("-&32%", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerInvalidProfileFormat() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "123"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningContainerInvalidProfile() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "B_BES"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningContainerReturnsOk() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    public void finalizeRemoteSigningContainerWithEmptyBody() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        JSONObject request = new JSONObject();
        Response response = putRemoteSigningInSession(flow, request, dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningContainerWithEmptySignatureValue() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(""), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningContainerWithInvalidSignatureValue() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_SIGNATURE);
    }

    @Test
    public void getRemoteSigningContainer() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToRemoteSigningContainer() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        response.then()
                .statusCode(405);
    }

    @Test
    public void optionsToRemoteSigningContainer() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToRemoteSigningContainer() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);
        expectError(response, 405, INVALID_REQUEST);
    }


    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
