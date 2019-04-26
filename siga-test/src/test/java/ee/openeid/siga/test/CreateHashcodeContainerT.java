package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class CreateHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void createHashcodeContainerEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptyDatafiles() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject request = new JSONObject();
        request.put("dataFiles", datafiles);
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerMissingFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerMissingSha256Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerMissingSha512Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerMissingFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateHashcodeContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptyFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest("", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptySha256Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, " ", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptySha512Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "    ", DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptyFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, ""));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest("?%*", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, "abc"));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidHash256() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, "+-KZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidHash512() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "+-Vz9wirVZNvP/q3HoaW8nu0FfvrGkZinhADKE4Y4j/dUuGfgONfR4VYdu0p/dj/yGH0qlE0FGsmUB2N3oLuhA==", DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidHash256length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE + "a", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerInvalidHash512length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateHashcodeContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE.substring(1), DEFAULT_FILESIZE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void deleteToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = delete(HASHCODE_CONTAINERS, flow);
        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void putToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = put(HASHCODE_CONTAINERS, flow, hashcodeContainersDataRequestWithDefault().toString());
        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void getToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = get(HASHCODE_CONTAINERS, flow);
        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }


    @Test
    public void headToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "HEAD", HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .head(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, "", "OPTIONS", HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .options(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getHeader("Allow"), equalTo("POST,OPTIONS"));
    }

    @Test
    public void patchToCreateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = given()
                .header(X_AUTHORIZATION_SIGNATURE, signRequest(flow, hashcodeContainersDataRequestWithDefault().toString(), "PATCH", HASHCODE_CONTAINERS, null, false))
                .header(X_AUTHORIZATION_TIMESTAMP, flow.getSigningTime())
                .header(X_AUTHORIZATION_SERVICE_UUID, flow.getServiceUuid())
                .config(RestAssured.config().encoderConfig(encoderConfig().defaultContentCharset("UTF-8")))
                .body(hashcodeContainersDataRequestWithDefault().toString())
                .log().all()
                .contentType(ContentType.JSON)
                .when()
                .patch(createUrl(HASHCODE_CONTAINERS))
                .then()
                .log().all()
                .extract()
                .response();
        assertThat(response.statusCode(), equalTo(405));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }
}
