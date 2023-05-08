package ee.openeid.siga.testgroovy.helper

import ee.openeid.siga.test.model.SigaApiFlow
import ee.openeid.siga.test.helper.LoggingFilter
import io.qameta.allure.Step
import io.qameta.allure.restassured.AllureRestAssured
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.Response
import org.json.JSONObject
import spock.lang.Specification

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.Callable

import static ee.openeid.siga.test.helper.TestData.*
import static ee.openeid.siga.test.utils.RequestBuilder.signRequest
import static io.restassured.RestAssured.given
import static io.restassured.config.EncoderConfig.encoderConfig
import static java.util.concurrent.TimeUnit.MILLISECONDS

import static org.awaitility.Awaitility.with

import static org.hamcrest.CoreMatchers.equalTo

abstract class TestBaseSpecification extends Specification {

        protected static Properties properties

        static {
            properties = new Properties()
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader()
                String path = classLoader.getResource("application-test.properties").getPath()
                properties.load(new FileInputStream(new File(path)))
            } catch (IOException e) {
                e.printStackTrace()
                throw new RuntimeException(e)
            }
            RestAssured.useRelaxedHTTPSValidation()

            boolean isLoggingEnabled = Boolean.parseBoolean(properties.getProperty("siga-test.logging.enabled"))
            if (isLoggingEnabled) {
                int characterSplitLimit = Integer.parseInt(properties.getProperty("siga-test.logging.character-split-limit"))
                RestAssured.filters(new AllureRestAssured(), new LoggingFilter(characterSplitLimit))
            } else {
                RestAssured.filters(new AllureRestAssured())
            }
        }

        @Step("Create container")
        protected Response postCreateContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
             return post(getContainerEndpoint(), flow, request.toString())
        }

        @Step("Upload container")
        protected Response postUploadContainer(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(UPLOAD + getContainerEndpoint(), flow, request.toString())
        }

        @Step("Validate container")
        protected Response postContainerValidationReport(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
             return post(getContainerEndpoint() + VALIDATIONREPORT, flow, request.toString())
        }

        @Step("Validate container in session")
        protected Response getValidationReportForContainerInSession(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return get(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow)
        }

        @Step("Start remote signing")
        protected Response postRemoteSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, request.toString())
        }

         @Step("Finalize remote signing")
        protected Response putRemoteSigningInSession(SigaApiFlow flow, JSONObject request, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
            return put(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + signatureId, flow, request.toString())
        }

        @Step("Start MID signing")
        protected Response postMidSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow, request.toString())
        }

        @Step("Get MID signing status")
        protected Response getMidSigningInSession(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
            Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow)
            flow.setMidStatus(response)
            return response
        }

        @Step("Start Smart-ID certificate choice")
        protected Response postSidCertificateChoice(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString())
        }

        @Step("Get Smart-ID certificate selection status")
        protected Response getSidCertificateStatus(SigaApiFlow flow, String generatedCertificateId) throws InvalidKeyException, NoSuchAlgorithmException {
            Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow)
            flow.setSidCertificateStatus(response)
            return response
        }

        @Step("Start Smart-ID signing")
        protected Response postSmartIdSigningInSession(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, request.toString())
        }

        @Step("Get Smart-ID signing status")
        protected Response getSmartIdSigningInSession(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
            Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow)
            flow.setSidStatus(response)
            return response
        }

        @Step("Get signature list")
        protected Response getSignatureList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return get(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow)
        }

        @Step("Get signature info")
        protected Response getSignatureInfo(SigaApiFlow flow, String signatureId) throws InvalidKeyException, NoSuchAlgorithmException {
            return get(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + signatureId, flow)
        }

        @Step("Get data file list")
        protected Response getDataFileList(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return get(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow)
        }

        @Step("Delete data file")
        protected Response deleteDataFile(SigaApiFlow flow, String dataFileName) throws InvalidKeyException, NoSuchAlgorithmException {
            return delete(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + dataFileName, flow)
        }

        @Step("add data file")
        protected Response addDataFile(SigaApiFlow flow, JSONObject request) throws InvalidKeyException, NoSuchAlgorithmException {
            return post(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/", flow, request.toString())
        }

        @Step("Get container")
        protected Response getContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return get(getContainerEndpoint() + "/" + flow.getContainerId(), flow)
        }

        @Step("Delete container")
        protected Response deleteContainer(SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return delete(getContainerEndpoint() + "/" + flow.getContainerId(), flow)
        }

        @Step("Poll for MID signing response")
        protected Response pollForMidSigning(SigaApiFlow flow, String signatureId) {
            with().pollInterval(3500, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(16000, MILLISECONDS)
                    .await("MID signing result")
                    .until(isMidFinished(flow, signatureId))

            return flow.getMidStatus()
        }

        private Callable<Boolean> isMidFinished(SigaApiFlow flow, String signatureId) {
            return new Callable<Boolean>() {
                 Boolean call() throws Exception {
                    return "OUTSTANDING_TRANSACTION" != getMidSigningInSession(flow, signatureId).getBody().path(MID_STATUS)
                }
            }
        }

        protected Response pollForSidCertificateStatus(SigaApiFlow flow, String generatedCertificateId) {
            return pollForSidCertificateStatusWithPollParameters(3500, 16000, flow, generatedCertificateId)
        }

        @Step("Poll for Smart-ID certificate status response")
        protected Response pollForSidCertificateStatusWithPollParameters(Integer pollTimeInMillis, Integer pollLengthInMillis, SigaApiFlow flow, String generatedCertificateId) {
            with().pollInterval(pollTimeInMillis, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(pollLengthInMillis, MILLISECONDS)
                    .await("Smart-ID certificate choice result")
                    .until(isSidCertificateStatusFinished(flow, generatedCertificateId))

            return flow.getSidCertificateStatus()
        }

        private Callable<Boolean> isSidCertificateStatusFinished(SigaApiFlow flow, String generatedCertificateId) {
            return new Callable<Boolean>() {
                 Boolean call() throws Exception {
                    return "OUTSTANDING_TRANSACTION" != getSidCertificateStatus(flow, generatedCertificateId).getBody().path(SMARTID_STATUS)
                }
            }
        }

        protected Response pollForSidSigning(SigaApiFlow flow, String signatureId) {
            return pollForSidSigningWithPollParameters(3500,18000, flow, signatureId)
        }

        @Step("Poll for Smart-ID signing response")
        protected Response pollForSidSigningWithPollParameters(Integer pollTimeInMillis, Integer pollLengthInMillis, SigaApiFlow flow, String signatureId) {
            with().pollInterval(pollTimeInMillis, MILLISECONDS).and().with().pollDelay(0, MILLISECONDS).atMost(pollLengthInMillis, MILLISECONDS)
                    .await("Smart-ID signing result")
                    .until(isSidFinished(flow, signatureId))

            return flow.getSidStatus()
        }

        private Callable<Boolean> isSidFinished(SigaApiFlow flow, String signatureId) {
            return new Callable<Boolean>() {
                Boolean call() throws Exception {
                    return "OUTSTANDING_TRANSACTION" != getSmartIdSigningInSession(flow, signatureId).getBody().path(SMARTID_STATUS)
                }
            }
        }

        protected String createUrl(String endpoint) {
            return properties.get("siga.protocol") + "://" + properties.get("siga.hostname") + ":" + properties.get("siga.port") + properties.get("siga.application-context-path") + endpoint
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
                    .contentType(ContentType.JSON)
                    .when()
                    .post(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
            if (response.getBody().path(CONTAINER_ID) != null) {
                flow.setContainerId(response.getBody().path(CONTAINER_ID).toString())
            }
            return response
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
                    .contentType(ContentType.JSON)
                    .when()
                    .put(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
            if (response.getBody().path(CONTAINER_ID) != null) {
                flow.setContainerId(response.getBody().path(CONTAINER_ID).toString())
            }
            return response
        }

        @Step("HTTP GET {0}")
        protected Response get(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return given()
                    .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "GET", endpoint))
                    .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                    .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                    .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                    .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                    .contentType(ContentType.JSON)
                    .when()
                    .get(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
        }

        @Step("HTTP DELETE {0}")
        protected Response delete(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return given()
                    .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "DELETE", endpoint))
                    .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                    .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                    .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                    .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                    .contentType(ContentType.JSON)
                    .when()
                    .delete(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
        }

        @Step("HTTP HEAD {0}")
        protected Response head(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return given()
                    .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", endpoint))
                    .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                    .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                    .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                    .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                    .contentType(ContentType.JSON)
                    .when()
                    .head(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
        }

        @Step("HTTP OPTIONS {0}")
        protected Response options(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return given()
                    .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", endpoint))
                    .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                    .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                    .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                    .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                    .contentType(ContentType.JSON)
                    .when()
                    .options(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
        }

        @Step("HTTP PATCH {0}")
        protected Response patch(String endpoint, SigaApiFlow flow) throws InvalidKeyException, NoSuchAlgorithmException {
            return given()
                    .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "PATCH", endpoint))
                    .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                    .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                    .header(X_AUTHORIZATION_HMAC_ALGO, flow.getHmacAlgorithm())
                    .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                    .contentType(ContentType.JSON)
                    .when()
                    .patch(createUrl(endpoint))
                    .then()
                    .extract()
                    .response()
        }

        abstract String getContainerEndpoint()

        protected void expectError(Response response, int status, String code) {
            response.then()
                    .statusCode(status)
                    .body(ERROR_CODE, equalTo(code))
        }

        protected void expectError(Response response, int status, String code, String message) {
            response.then()
                    .statusCode(status)
                    .body(ERROR_CODE, equalTo(code))
                    .body(ERROR_MESSAGE, equalTo(message))
        }

        protected void expectMidStatus(Response response, String message) {
            response.then()
                    .statusCode(200)
                    .body(MID_STATUS, equalTo(message))
        }

        protected void expectSmartIdStatus(Response response, String message) {
            response.then()
                    .statusCode(200)
                    .body(SMARTID_STATUS, equalTo(message))
        }
}
