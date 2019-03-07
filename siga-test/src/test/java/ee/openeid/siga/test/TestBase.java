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
        return post(HASHCODE_CONTAINERS, flow, request);
    }

     protected Response postUploadHashcodeContainer(SigaApiFlow flow, String request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(UPLOAD + HASHCODE_CONTAINERS, flow, request);
    }

    protected Response postHashcodeContainerValidationReport(SigaApiFlow flow, String request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS+VALIDATIONREPORT, flow, request);
    }

    protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);
    }

    protected Response postRemoteSigningInSession(SigaApiFlow flow, String request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTESIGNING, flow, request);
    }

    private Response post(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request).log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(endpoint)
                .then()
                .log().all()
                .extract()
                .response();
        if (response.getBody().path(CONTAINER_ID) != null){
            flow.setContainerId(response.getBody().path(CONTAINER_ID).toString());
        }
        return response;
    }

    private Response get(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(endpoint)
                .then()
                .log().all()
                .extract()
                .response();
    }
}
