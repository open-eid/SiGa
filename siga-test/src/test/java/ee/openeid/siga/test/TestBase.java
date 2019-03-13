package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;

public class TestBase {

    protected Response postCreateHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS, flow, request.toString());
    }

     protected Response postUploadHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(UPLOAD + HASHCODE_CONTAINERS, flow, request.toString());
    }

    protected Response postHashcodeContainerValidationReport(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS+VALIDATIONREPORT, flow, request.toString());
    }

    protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);
    }

    protected Response postHashcodeRemoteSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, request.toString());
    }

    protected Response putHashcodeRemoteSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return put(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, request.toString());
    }

    protected Response postHashcodeMidSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + MID_SIGNING, flow, request.toString());
    }

    protected Response getHashcodeMidSigningInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + MID_SIGNING + STATUS, flow);
    }

    protected Response getHashcodeSignatureList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + SIGNATURES, flow);
    }

    protected Response getHashcodeContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
    }

    protected Response deleteHashcodeContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return delete(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
    }

    protected Response pollForMidSigning (SigaApiFlow flow) throws InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        Long endTime = Instant.now().getEpochSecond()  + 15;
        while (Instant.now().getEpochSecond() < endTime) {
            Thread.sleep(3500);
            Response response = getHashcodeMidSigningInSession(flow);
            if ("SIGNATURE".equals(response.getBody().path("midStatus"))) {
                return response;
            }
        }
        throw new RuntimeException("No MID response in: 15 seconds");
    }


    private Response post(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "POST", endpoint, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request)
                .log().all()
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

    private Response put(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "PUT", endpoint, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request)
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .put(endpoint)
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
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "GET", endpoint, null))
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

    private Response delete(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "DELETE", endpoint, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .delete(endpoint)
                .then()
                .log().all()
                .extract()
                .response();
    }


}
