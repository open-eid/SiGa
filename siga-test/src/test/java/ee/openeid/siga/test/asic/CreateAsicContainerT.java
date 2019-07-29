package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class CreateAsicContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createAsicContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequestWithDefault());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void createAsicContainerEmptyBody() throws NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerEmptyDatafiles() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject request = new JSONObject();
        request.put("dataFiles", datafiles);
        request.put("containerName", DEFAULT_CONTAINER_NAME);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerMissingContainerName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, null));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerMissingFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(null, DEFAULT_DATAFILE_CONTENT, DEFAULT_CONTAINER_NAME));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerMissingDataFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, null, DEFAULT_CONTAINER_NAME));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerEmptyContainerName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, ""));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerEmptyFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest("", DEFAULT_DATAFILE_CONTENT, DEFAULT_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerEmptyFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, "", DEFAULT_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }


    @Test
    public void createAsicContainerInvalidFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest("?%*", DEFAULT_DATAFILE_CONTENT, DEFAULT_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerFileInFolder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest("folder/test.txt", DEFAULT_DATAFILE_CONTENT, DEFAULT_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerInvalidDataFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, "=", DEFAULT_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerInvalidContainerName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException{
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, "?%*"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerContainerInFolder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException{
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, "folder/test.txt"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void deleteToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = delete(getContainerEndpoint(), flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = put(getContainerEndpoint(), flow, asicContainersDataRequestWithDefault().toString());
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = get(getContainerEndpoint(), flow);
        expectError(response, 405, INVALID_REQUEST);
    }


    @Test
    public void headToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = head(getContainerEndpoint(), flow);
        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = options(getContainerEndpoint(), flow);
        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToCreateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = patch(getContainerEndpoint(), flow);
        expectError(response, 405, INVALID_REQUEST);
    }


    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
