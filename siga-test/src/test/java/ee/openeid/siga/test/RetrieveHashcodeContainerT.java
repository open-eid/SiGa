package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeMidSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.digestSigner.signDigest;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class RetrieveHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = new SigaApiFlow();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveIt() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void createHashcodeContainerAndRetrieve() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(1440));
    }

    @Test
    public void retrieveHashcodeContainerTwice() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        getHashcodeContainer(flow);
        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(1440));
    }

    @Test
    public void retrieveHashcodeContainerBeforeSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerAfterSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), Matchers.greaterThanOrEqualTo(3500));
    }

    @Test
    public void retrieveHashcodeContainerBeforeFinishingMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerDuringMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        getHashcodeMidSigningInSession(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerAfterMidSigning() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException, InterruptedException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766"));
        pollForMidSigning(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), Matchers.greaterThanOrEqualTo(35000));
    }

    @Test
    public void retrieveHashcodeContainerAfterValidation() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerAfterRetrievingSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getHashcodeSignatureList(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER).toString().length(), equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerForOtherClientNotPossible() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAndRetrieveIt() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteHashcodeContainer(flow);

        Response response = getHashcodeContainer(flow);

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void postToGetHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, IOException, JSONException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = post(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow, "");
        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void headToGetHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", HASHCODE_CONTAINERS + "/" + flow.getContainerId(), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(HASHCODE_CONTAINERS + "/" + flow.getContainerId())
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToGetHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", HASHCODE_CONTAINERS + "/" + flow.getContainerId(), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(HASHCODE_CONTAINERS + "/" + flow.getContainerId())
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getHeader("Allow"), equalTo("GET,HEAD,DELETE,OPTIONS"));
    }

    @Ignore //TODO: SIGARIA-50
    @Test
    public void patchToGetHashcodeContainer () throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainersDataRequestWithDefault().toString(), "PATCH", HASHCODE_CONTAINERS + "/" + flow.getContainerId(), null))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainersDataRequestWithDefault().toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(HASHCODE_CONTAINERS + "/" + flow.getContainerId())
                .then()
                .log().all()
                .extract()
                .response();

        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }
}
