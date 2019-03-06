package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;

public class TestBase {

    protected Response postCreateHashcodeContainer(SigaApiFlow flow, String request) throws InvalidKeyException, NoSuchAlgorithmException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request).log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(HASHCODE_CONTAINERS)
                .then()
                .log().all()
                .extract()
                .response();
        flow.setContainerId(response.getBody().path(CONTAINER_ID).toString());
        return response;
    }

     protected Response postUploadHashcodeContainer(SigaApiFlow flow, String request) throws InvalidKeyException, NoSuchAlgorithmException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request).log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(UPLOAD_HASHCODE_CONTAINERS)
                .then()
                .log().all()
                .extract()
                .response();
        flow.setContainerId(response.getBody().path(CONTAINER_ID).toString());
        return response;
    }

    protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + VALIDATIONREPORT)
                .then()
                .log().all()
                .extract()
                .response();
    }
}
