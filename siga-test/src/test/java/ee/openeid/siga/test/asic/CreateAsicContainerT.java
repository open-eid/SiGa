package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import eu.europa.esig.dss.model.MimeType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.CONTAINER;
import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.CONTAINER_ID;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_DATAFILE_CONTENT;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.ERROR_CODE;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.TEST_FILE_EXTENSIONS;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestDataFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSigaProfileActive("datafileContainer")
public class CreateAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
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
    public void createAsicContainerMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> asicContainersDataRequestDataFile("filename." + ext, DEFAULT_DATAFILE_CONTENT))
                .collect(Collectors.toList()), DEFAULT_ASICE_CONTAINER_NAME));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (1 + i) + "].@manifest:media-type"));
        }
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
        request.put("containerName", DEFAULT_ASICE_CONTAINER_NAME);
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
        Response response = postCreateContainer(flow, asicContainersDataRequest(null, DEFAULT_DATAFILE_CONTENT, DEFAULT_ASICE_CONTAINER_NAME));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createAsicContainerMissingDataFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, null, DEFAULT_ASICE_CONTAINER_NAME));
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
        Response response = postCreateContainer(flow, asicContainersDataRequest("", DEFAULT_DATAFILE_CONTENT, DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerEmptyFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, "", DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerInvalidFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest("?%*", DEFAULT_DATAFILE_CONTENT, DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerFileInFolder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest("folder/test.txt", DEFAULT_DATAFILE_CONTENT, DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerInvalidDataFileContent() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, "=", DEFAULT_ASICE_CONTAINER_NAME));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerInvalidContainerName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException{
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, "?%*"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createAsicContainerPathInContainerName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException{
        Response response = postCreateContainer(flow, asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, "C://folder/test.asice"));
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
