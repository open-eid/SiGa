package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.digestSigner.signDigest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class RemoteSigningHachcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void startRemoteSigningHashcodeContainerReturnsDigestToSign() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(DATA_TO_SIGN).toString().length(), equalTo(1712));
        assertThat(response.getBody().path(DIGEST_ALGO), equalTo("SHA512"));
    }

    @Ignore //TODO: SIGARIA-52
    @Test
    public void startRemoteSigningHashcodeContainerWithAllParamsReturnsDigestToSign() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn, Estonia"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(DATA_TO_SIGN).toString().length(), equalTo(1712));
        assertThat(response.getBody().path(DIGEST_ALGO), equalTo("SHA512"));
    }

    @Ignore //TODO: SIGARIA-52
    @Test
    public void startRemoteSigningHashcodeContainerWithRoleReturnsDigestToSign() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", null));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(DATA_TO_SIGN).toString().length(), equalTo(1712));
        assertThat(response.getBody().path(DIGEST_ALGO), equalTo("SHA512"));
    }

    @Ignore //TODO: SIGARIA-52
    @Test
    public void startRemoteSigningHashcodeContainerWithLocationReturnsDigestToSign() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequest(SIGNER_CERT_PEM, "LT", null, "Tallinn, Estonia"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(DATA_TO_SIGN).toString().length(), equalTo(1712));
        assertThat(response.getBody().path(DIGEST_ALGO), equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        JSONObject request = new JSONObject();
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingSigningCertificate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingProfile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_PEM);
        Response response = postHashcodeRemoteSigningInSession(flow, request);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptySigningCertificate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault("", "LT"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyProfile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, ""));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidSigningCertificate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault("-&32%", "LT"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "123"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerReturnsOk() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo("OK"));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        JSONObject request = new JSONObject();
        Response response = putHashcodeRemoteSigningInSession(flow, request);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptySignatureValue() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(""));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Ignore //TODO: SIGARIA-52
    @Test
    public void finalizeRemoteSigningHashcodeContainerWithInvalidSignatureValue() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest("12345"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void getRemoteSigningHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void headToRemoteSigningHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING)
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void optionsToRemoteSigningHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING)
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getHeader("Allow"), equalTo("POST,PUT,OPTIONS"));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void patchToRemoteSigningHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest("hashcode.asice"));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainersDataRequestWithDefault().toString(), "PATCH", HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainersDataRequestWithDefault().toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING)
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }
}
