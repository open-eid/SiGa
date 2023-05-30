package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Security;
import java.time.Instant;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

class AuthenticationT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void serviceDisabled() throws Exception {
        flow.setServiceUuid(SERVICE_UUID_6);
        flow.setServiceSecret(SERVICE_SECRET_6);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void uuidAndSecretMismatch() throws Exception {
        flow.setServiceUuid(SERVICE_UUID_2);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void defaultAlgoHmacSHA256HeaderMissing() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .body(CONTAINER_ID, notNullValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HmacSHA256", "HmacSHA384", "HmacSHA512"})
    void algoHmacExplicitlySet(String hmacAlgorithm) throws Exception {
        flow.setHmacAlgorithm(hmacAlgorithm);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HmacSHA3-256", "HmacSHA3-384", "HmacSHA3-512"})
    void algoHmacSHA3ExplicitlySet(String hmacAlgorithm) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        flow.setHmacAlgorithm(hmacAlgorithm);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    void notDefaultAlgoUsedAndNotSpecifiedInHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setHmacAlgorithm("HmacSHA512");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HmacSHA512/224", "HmacSHA224", "HmacSHA1"})
    void algoHmacExplicitlySetShouldNotBeAllowed(String hmacAlgorithm) throws Exception {
        flow.setHmacAlgorithm(hmacAlgorithm);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void algoSetInHeaderAndActuallyUsedDoNotMatch() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setHmacAlgorithm("HmacSHA512");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void notExistingHmacAlgoInHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setHmacAlgorithm("HmacSHA512");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_HMAC_ALGO, "SomeRandomAlgo")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void missingServiceUuidHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void missingSignatureHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_TIMESTAMP, "1555443270")
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }


    @Test
    void missingTimeStampHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
                given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void nonExistingUuid() throws Exception {
        flow.setServiceUuid("a3a2a728-a3ea-4975-bfab-f240a67e894f");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void wrongSigningSecret() throws Exception {
        flow.setServiceSecret("746573715365637265724b6579304031");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void wrongMethodInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "PUT", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void wrongUrlInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", VALIDATIONREPORT))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void missingBodyInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, null, "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void wrongOrderInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), HASHCODE_CONTAINERS, "POST"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void signatureFromDifferentRequest() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        String signature = signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS);
        JSONObject request2 = hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME);
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signature)
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request2.toString())
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    void signingTimeInFuture() throws Exception {
        Long signingTime = Instant.now().getEpochSecond() + 30;

        flow.setSigningTime(signingTime.toString());
        flow.setForceSigningTime(true);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    void signingTimeInPast() throws Exception {
        Long signingTime = Instant.now().getEpochSecond() - 120;

        flow.setSigningTime(signingTime.toString());
        flow.setForceSigningTime(true);
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
