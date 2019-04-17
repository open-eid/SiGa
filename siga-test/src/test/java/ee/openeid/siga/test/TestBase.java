package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.test.utils.RequestBuilder;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Properties;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;

public class TestBase {

    protected static Properties properties;

    static {
        properties = new Properties();
        try {
            ClassLoader classLoader = RequestBuilder.class.getClassLoader();
            String path = classLoader.getResource("application-test.properties").getPath();
            properties.load(new FileInputStream(new File(path)));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    protected Response postCreateHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS, flow, request.toString());
    }

    protected Response postUploadHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(UPLOAD + HASHCODE_CONTAINERS, flow, request.toString());
    }

    protected Response postHashcodeContainerValidationReport(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + VALIDATIONREPORT, flow, request.toString());
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

    protected Response pollForMidSigning(SigaApiFlow flow) throws InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        Long endTime = Instant.now().getEpochSecond() + 15;
        while (Instant.now().getEpochSecond() < endTime) {
            Thread.sleep(3500);
            Response response = getHashcodeMidSigningInSession(flow);
            if (!"OUTSTANDING_TRANSACTION".equals(response.getBody().path(MID_STATUS))) {
                return response;
            }
        }
        throw new RuntimeException("No MID response in: 15 seconds");
    }

    protected String createUrl(String endpoint) {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint;
    }

    protected String createUrlToSign(String endpoint) {
        return properties.getProperty("siga.application-context-path") + endpoint;
    }

    protected Response post(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "POST", createUrlToSign(endpoint), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request)
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
        if (response.getBody().path(CONTAINER_ID) != null) {
            flow.setContainerId(response.getBody().path(CONTAINER_ID).toString());
        }
        return response;
    }

    protected Response put(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "PUT", createUrlToSign(endpoint), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request)
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .put(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
        if (response.getBody().path(CONTAINER_ID) != null) {
            flow.setContainerId(response.getBody().path(CONTAINER_ID).toString());
        }
        return response;
    }

    protected Response get(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "GET", createUrlToSign(endpoint), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .get(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
    }

    protected Response delete(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "DELETE", createUrlToSign(endpoint), null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .delete(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
    }


}
