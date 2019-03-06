package ee.openeid.siga.test;

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

    protected Response postCreateHashcodeContainer(String request) throws InvalidKeyException, NoSuchAlgorithmException {
        Long timestamp =  System.currentTimeMillis()/1000;
        return given()
                .header(X_AUTHORIZATION_TIMESTAMP, timestamp)
                .header(X_AUTHORIZATION_SERVICE_UUID, SERVICE_UUID)
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(SERVICE_SECRET, SERVICE_UUID, timestamp, request, null))
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request).log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(HASHCODE_CONTAINERS)
                .then()
                .log().all()
                .extract()
                .response();
    }

     protected Response postUploadHashcodeContainer(String request) throws InvalidKeyException, NoSuchAlgorithmException {
        Long timestamp =  System.currentTimeMillis()/1000;
        return given()
                .header(X_AUTHORIZATION_TIMESTAMP, timestamp)
                .header(X_AUTHORIZATION_SERVICE_UUID, SERVICE_UUID)
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(SERVICE_SECRET, SERVICE_UUID, timestamp, request, null))
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request).log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(UPLOAD_HASHCODE_CONTAINERS)
                .then()
                .log().all()
                .extract()
                .response();
    }

    protected Response getValidationReportForContainerInSession(String containerId) throws InvalidKeyException, NoSuchAlgorithmException {
        Long timestamp =  System.currentTimeMillis()/1000;
        return given()
                .header(X_AUTHORIZATION_TIMESTAMP, timestamp)
                .header(X_AUTHORIZATION_SERVICE_UUID, SERVICE_UUID)
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(SERVICE_SECRET, SERVICE_UUID, timestamp, "", null))
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(HASHCODE_CONTAINERS + "/" + containerId + VALIDATIONREPORT)
                .then()
                .log().all()
                .extract()
                .response();
    }
}
