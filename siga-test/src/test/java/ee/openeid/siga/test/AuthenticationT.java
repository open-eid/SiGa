package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.security.Security;
import java.time.Instant;

import static ee.openeid.siga.test.helper.TestData.*;
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
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void defaultAlgoHmacSHA256HeaderMissing() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
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
        flow.setHmacAlgorithm("HmacSHA256");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA384ExplicitlySet() throws Exception {
        flow.setHmacAlgorithm("HmacSHA384");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }
    
    @Test
    public void algoHmacSHA512ExplicitlySet() throws Exception {
        flow.setHmacAlgorithm("HmacSHA512");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA3_256ExplicitlySet() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        flow.setHmacAlgorithm("HmacSHA3-256");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA3_384ExplicitlySet() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        flow.setHmacAlgorithm("HmacSHA3-384");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void algoHmacSHA3_512ExplicitlySet() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        flow.setHmacAlgorithm("HmacSHA3-512");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID, notNullValue());
    }

    @Test
    public void notDefaultAlgoUsedAndNotSpecifiedInHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setHmacAlgorithm("HmacSHA512");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
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
    public void algoHmacSHA3_512_224ExplicitlySetShouldNotBeAllowed() throws Exception {
        flow.setHmacAlgorithm("HmacSHA512/224");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void algoHmacSHA224ExplicitlySetShouldNotBeAllowed() throws Exception {
        flow.setHmacAlgorithm("HmacSHA224");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void algoHmacSHA1ExplicitlySetShouldNotBeAllowed() throws Exception {
        flow.setHmacAlgorithm("HmacSHA1");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void algoSetInHeaderAndActuallyUsedDoNotMatch() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        flow.setHmacAlgorithm("HmacSHA512");
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
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
    public void missingServiceUuidHeader() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
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
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS))
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
        flow.setServiceUuid("a3a2a728-a3ea-4975-bfab-f240a67e894f");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void wrongSigningSecret() throws Exception {
        flow.setServiceSecret("746573715365637265724b6579304031");
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void wrongMethodInSignature() throws Exception {
        JSONObject request = hashcodeContainersDataRequestWithDefault();
        given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "PUT", HASHCODE_CONTAINERS))
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
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), "POST", VALIDATIONREPORT))
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
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, null, "POST", HASHCODE_CONTAINERS))
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
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, request.toString(), HASHCODE_CONTAINERS, "POST"))
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
        String signature = signRequest(flow, request.toString(), "POST", HASHCODE_CONTAINERS);
        JSONObject request2 = hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME);
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
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        expectError(response, 401, AUTHORIZATION_ERROR);
    }

    @Test
    public void signingTimeInPast() throws Exception {
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
