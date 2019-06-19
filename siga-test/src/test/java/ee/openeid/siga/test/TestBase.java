package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.test.utils.RequestBuilder;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.concurrent.Callable;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.with;

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
        RestAssured.useRelaxedHTTPSValidation();
        RestAssured.filters(new AllureRestAssured());
    }

    @Step ("Create hashcode container")
    protected Response postCreateHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS, flow, request.toString());
    }

    @Step ("Upload hashcode container")
    protected Response postUploadHashcodeContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(UPLOAD + HASHCODE_CONTAINERS, flow, request.toString());
    }

    @Step ("Validate hashcode container")
    protected Response postHashcodeContainerValidationReport(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + VALIDATIONREPORT, flow, request.toString());
    }

    @Step ("Validate hashcode container in session")
    protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);
    }

    @Step ("Start remote signing")
    protected Response postHashcodeRemoteSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, request.toString());
    }

    @Step ("Finalize remote signing")
    protected Response putHashcodeRemoteSigningInSession(SigaApiFlow flow, JSONObject request, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        return put(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + signatureId, flow, request.toString());
    }

    @Step ("Start MID signing")
    protected Response postHashcodeMidSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + MID_SIGNING, flow, request.toString());
    }

    @Step ("Get MID signing status")
    protected Response getHashcodeMidSigningInSession(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        Response response = get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);
        flow.setMidStatus(response);
        return response;
    }

    @Step ("Get signature list")
    protected Response getHashcodeSignatureList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId() + SIGNATURES, flow);
    }

    @Step ("Get container")
    protected Response getHashcodeContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
    }

    @Step ("Delete container")
    protected Response deleteHashcodeContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return delete(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
    }

    @Step ("Poll for MID signing response")
    protected Response pollForMidSigning(SigaApiFlow flow, String signatureId) throws NoSuchAlgorithmException, InvalidKeyException {
        with().pollInterval(3500, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(15000, MILLISECONDS)
                .await("MID signing result")
                .until(isMidFinished(flow, signatureId));

        return flow.getMidStatus();
    }

    private Callable<Boolean> isMidFinished(SigaApiFlow flow, String signatureId) {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return !"OUTSTANDING_TRANSACTION".equals(getHashcodeMidSigningInSession(flow, signatureId).getBody().path(MID_STATUS));
            }
        };
    }

    protected String createUrl(String endpoint) {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint;
    }

    @Step ("HTTP POST {0}")
    protected Response post(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "POST", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
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

    @Step ("HTTP PUT {0}")
    protected Response put(String endpoint, SigaApiFlow flow, String request) throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request, "PUT", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
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

    @Step ("HTTP GET {0}")
    protected Response get(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "GET", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
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

    @Step ("HTTP DELETE {0}")
    protected Response delete(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "DELETE", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
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

    @Step ("HTTP HEAD {0}")
    protected Response head(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
    }

    @Step ("HTTP OPTIONS {0}")
    protected Response options(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
    }

    @Step ("HTTP PATCH {0}")
    protected Response patch(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "PATCH", endpoint))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(createUrl(endpoint))
                .then()
                .log().all()
                .extract()
                .response();
    }
}
