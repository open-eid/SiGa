package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
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
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path("dataToSign"), dataToSignResponse.getBody().path("digestAlgorithm"))));

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    public void startRemoteSigningHashcodeContainerReturnsDigestToSign() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", null,null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyBody() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingSigningCertificate() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingProfile() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_PEM);
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptySigningCertificate() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault("", "LT"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyProfile() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, ""));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidSigningCertificate() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault("-&32%", "LT"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfile() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "123"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerReturnsOk() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo("OK"));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptyBody() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        JSONObject request = new JSONObject();
        Response response = putHashcodeRemoteSigningInSession(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptySignatureValue() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(""));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithInvalidSignatureValue() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_SIGNATURE));
    }

    @Test
    public void getRemoteSigningHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        response.then()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

     @Test
    public void headToRemoteSigningHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", createUrlToSign(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(createUrl(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING))
                .then()
                .log().all()
                .statusCode(405);
    }

    @Ignore //TODO: Flaky test due to Allow method order change
    @Test
    public void optionsToRemoteSigningHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", createUrlToSign(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(createUrl(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING))
                .then()
                .log().all()
                .statusCode(200)
                .header("Allow", equalTo("PUT,POST,OPTIONS"));
    }

    @Test
    public void patchToRemoteSigningHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainersDataRequestWithDefault().toString(), "PATCH", createUrlToSign(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainersDataRequestWithDefault().toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(createUrl(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING))
                .then()
                .log().all()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }
}
