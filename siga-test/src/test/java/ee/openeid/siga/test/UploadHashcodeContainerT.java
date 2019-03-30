package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class UploadHashcodeContainerT extends TestBase {


    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void uploadHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test //TODO: At what point the container structure should be validated?
    public void uploadInvalidHashcodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha256File.asice"));
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void uploadHashcodeContainerWithoutSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void uploadHashcodeContainerEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        Response response = postUploadHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerEmptyContainerField() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        request.put("container", "");
        Response response = postUploadHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerNotBase64Container() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");
        Response response = postUploadHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerNotValidContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));
        Response response = postUploadHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void deleteToUploadHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = delete(UPLOAD + HASHCODE_CONTAINERS, flow);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void putToUploadHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        Response response = put(UPLOAD + HASHCODE_CONTAINERS, flow, hashcodeContainerRequestFromFile("hashcode.asice").toString());
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void getToUploadHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = get(UPLOAD + HASHCODE_CONTAINERS, flow);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void headToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", createUrlToSign(UPLOAD + HASHCODE_CONTAINERS), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void optionsToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", createUrlToSign(UPLOAD + HASHCODE_CONTAINERS), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getHeader("Allow"), equalTo("POST,OPTIONS"));
    }

    @Test
    public void patchToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainerRequestFromFile("hashcode.asice").toString(), "PATCH", createUrlToSign(UPLOAD + HASHCODE_CONTAINERS), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainerRequestFromFile("hashcode.asice").toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }
}
