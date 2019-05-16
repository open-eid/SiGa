package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class AuthenticationT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uuidAndSecretMismatch() throws Exception {
        flow.setServiceUuid(SERVICE_UUID_2);
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void defaultAlgoHmacSHA256() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA256ExplicitlySet() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA256"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA384ExplicitlySet() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA384"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA384")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA512ExplicitlySet() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA512"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA512")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void notDefaultAlgoUsedAndNotSpecifiedInHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA512"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Ignore //TODO: How to use SHA3 algos
    @Test
    public void algoHmacSHA3224ExplicitlySet() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA3-224"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA3-224")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA224ExplicitlySetShouldNotBeAllowed() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA224"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA224")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void algoHmacSHA1ExplicitlySetShouldNotBeAllowed() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA1"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA1")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void algoSetInHeaderAndActuallyUsedDoNotMatch() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA256"))
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void missingServiceUuid() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA512"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA512")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void missingSignatureHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_TIMESTAMP, "1555443270")
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }


    @Test
    public void missingTimeStampHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, "HmacSHA512"))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .header(X_AUTHORIZATION_HMAC_ALGO, "HmacSHA256")
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void nonExistingUuid() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setServiceUuid("a3a2a728-a3ea-4975-bfab-f240a67e894f");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void wrongSigningSecret() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setServiceSecret("746573715365637265724b6579304031");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void wrongMethodInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "PUT", HASHCODE_CONTAINERS, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void wrongUrlInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", VALIDATIONREPORT, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void missingBodyInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, null, "POST", HASHCODE_CONTAINERS, null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void wrongOrderInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), HASHCODE_CONTAINERS, "POST", null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void signatureFromDifferentRequest() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        String signature = signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS, null);
        JSONObject request2 = hashcodeContainerRequestFromFile("hashcode.asice");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signature)
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(request2.toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .post(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void signingTimeInFuture() throws Exception {
        Long signingTime = Instant.now().getEpochSecond() + 30;

        flow.setSigningTime(signingTime.toString());
        flow.setForceSigningTime(true);
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }

    @Test
    public void signingTimeInPast() throws Exception {
        Long signingTime = Instant.now().getEpochSecond() - 30;

        flow.setSigningTime(signingTime.toString());
        flow.setForceSigningTime(true);
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(401)
                .body(ERROR_CODE, equalTo(AUTHORIZATION_ERROR));
    }
}
