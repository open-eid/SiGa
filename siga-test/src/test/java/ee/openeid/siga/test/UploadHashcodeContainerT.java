package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.*;

public class UploadHashcodeContainerT extends TestBase {


    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test //TODO: More elaborate rules should be checked on container ID
    public void uploadHashcodeContainerShouldReturnContainerId() throws Exception {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void uploadInvalidHashcodeContainer() throws Exception {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha512File.asice"));

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_CONTAINER));
    }

    @Test
    public void uploadHashcodeContainerWithoutSignatures() throws Exception {
        Response response = postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void uploadHashcodeContainerEmptyBody() throws Exception {
        JSONObject request = new JSONObject();
        Response response = postUploadHashcodeContainer(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerEmptyContainerField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "");

        Response response = postUploadHashcodeContainer(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerNotBase64Container() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");

        Response response = postUploadHashcodeContainer(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void uploadHashcodeContainerNotValidContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));

        Response response = postUploadHashcodeContainer(flow, request);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_CONTAINER));
    }

    @Test
    public void deleteToUploadHashcodeContainer() throws Exception {
        Response response = delete(UPLOAD + HASHCODE_CONTAINERS, flow);

        response.then()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void putToUploadHashcodeContainer() throws Exception {
        Response response = put(UPLOAD + HASHCODE_CONTAINERS, flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER).toString());

        response.then()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void getToUploadHashcodeContainer() throws Exception {
        Response response = get(UPLOAD + HASHCODE_CONTAINERS, flow);

        response.then()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }

    @Test
    public void headToCreateHashcodeContainer() throws Exception {
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", UPLOAD + HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(405);
    }

    @Test
    public void optionsToCreateHashcodeContainer() throws Exception {
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", UPLOAD + HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));    }

    @Test
    public void patchToCreateHashcodeContainer() throws Exception {
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER).toString(), "PATCH", UPLOAD + HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER).toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(createUrl(UPLOAD + HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }
}
