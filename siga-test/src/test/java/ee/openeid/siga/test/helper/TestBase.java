package ee.openeid.siga.test.helper;

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

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.with;
import static org.hamcrest.CoreMatchers.equalTo;

public abstract class TestBase {

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

    @Step("Create container")
    protected Response postCreateContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(getContainerEndpoint(), flow, request.toString());
    }

    @Step("Upload container")
    protected Response postUploadContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(UPLOAD + getContainerEndpoint(), flow, request.toString());
    }

    @Step("Validate container")
    protected Response postContainerValidationReport(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(getContainerEndpoint() + VALIDATIONREPORT, flow, request.toString());
    }

    @Step("Validate container in session")
    protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);
    }

    @Step("Start remote signing")
    protected Response postRemoteSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, request.toString());
    }

    @Step("Finalize remote signing")
    protected Response putRemoteSigningInSession(SigaApiFlow flow, JSONObject request, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        return put(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + signatureId, flow, request.toString());
    }

    @Step("Start MID signing")
    protected Response postMidSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow, request.toString());
    }

    @Step("Get MID signing status")
    protected Response getMidSigningInSession(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);
        flow.setMidStatus(response);
        return response;
    }

    @Step("Start Smart-ID signing")
    protected Response postSmartIdSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
        return post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, request.toString());
    }

    @Step("Get Smart-ID signing status")
    protected Response getSmartIdSigningInSession(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);
        flow.setSidStatus(response);
        return response;
    }

    @Step("Get signature list")
    protected Response getSignatureList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);
    }

    @Step("Get signature info")
    protected Response getSignatureInfo(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + signatureId, flow);
    }

    @Step("Get data file list")
    protected Response getDataFileList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);
    }

    @Step("Get container")
    protected Response getContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return get(getContainerEndpoint() + "/" + flow.getContainerId(), flow);
    }

    @Step("Delete container")
    protected Response deleteContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
        return delete(getContainerEndpoint() + "/" + flow.getContainerId(), flow);
    }

    @Step("Poll for MID signing response")
    protected Response pollForMidSigning(SigaApiFlow flow, String signatureId) {
        with().pollInterval(3500, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(16000, MILLISECONDS)
                .await("MID signing result")
                .until(isMidFinished(flow, signatureId));

        return flow.getMidStatus();
    }

    private Callable<Boolean> isMidFinished(SigaApiFlow flow, String signatureId) {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return !"OUTSTANDING_TRANSACTION".equals(getMidSigningInSession(flow, signatureId).getBody().path(MID_STATUS));
            }
        };
    }

    @Step("Poll for Smart-ID signing response")
    protected Response pollForSidSigning(SigaApiFlow flow, String signatureId) {
        with().pollInterval(3500, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(16000, MILLISECONDS)
                .await("Smart-ID signing result")
                .until(isSidFinished(flow, signatureId));

        return flow.getSidStatus();
    }

    private Callable<Boolean> isSidFinished(SigaApiFlow flow, String signatureId) {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return !"RUNNING".equals(getSmartIdSigningInSession(flow, signatureId).getBody().path(SMARTID_STATUS));
            }
        };
    }

    protected String createUrl(String endpoint) {
        return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint;
    }

    @Step("HTTP POST {0}")
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

    @Step("HTTP PUT {0}")
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

    @Step("HTTP GET {0}")
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

    @Step("HTTP DELETE {0}")
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

    @Step("HTTP HEAD {0}")
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

    @Step("HTTP OPTIONS {0}")
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

    @Step("HTTP PATCH {0}")
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

    public abstract String getContainerEndpoint();

    protected void expectError(Response response, int code, String message) {
        response.then()
                .statusCode(code)
                .body(ERROR_CODE, equalTo(message));
    }

    protected void expectMidStatus(Response response, String message) {
        response.then()
                .statusCode(200)
                .body(MID_STATUS, equalTo(message));
    }
}
