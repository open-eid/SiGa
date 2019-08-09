package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ManipulateDataFilesAsicContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadAsicContainerAndRetrieveDataFilesList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo("test.xml"))
                .body("dataFiles[0].fileContent", startsWith("PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz"))
                .body("dataFiles[1].fileName", equalTo("test.txt"))
                .body("dataFiles[1].fileContent", equalTo("c2VlIG9uIHRlc3RmYWls"));
    }

    @Test
    public void uploadAsicContainerWithoutSignaturesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo("test.txt"))
                .body("dataFiles[0].fileContent", equalTo("c2VlIG9uIHRlc3RmYWls"));
    }

    @Test
    public void uploadAsicContainerWithoutDataFilesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_data_files.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Test
    public void uploadAsicContainerWithInvalidSignatureAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknown_ocsp.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo("test.txt"))
                .body("dataFiles[0].fileContent", equalTo("MTIzCg=="));
    }

    @Test
    public void createAsicContainerAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = getDataFileList(flow);

        getContainer(flow);
        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileContent", equalTo(DEFAULT_DATAFILE_CONTENT));
    }

    @Ignore ("Requires DD4J 3.2.1")
    @Test
    public void createAsicContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        response.then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Ignore ("Requires DD4J 3.2.1")
    @Test
    public void uploadAsicContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        response.then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Test
    public void uploadAsicContainerAndRemoveNotExistingDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        Response response = deleteDataFile(flow, "random.txt");

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void uploadAsicContainerWithSignaturesAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[1].fileName"));

        expectError(response, 400, INVALID_DATA);
    }

    @Ignore ("Requires DD4J 3.2.1")
    @Test
    public void uploadAsicContainerWithSpecialCharactersAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("Nonconventional_characters_in_data_file.asice"));

        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Test
    public void uploadAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileContent", startsWith("c2VlIG9uIHRlc3RmYWls"))
                .body("dataFiles[1].fileName", equalTo("testFile.txt"))
                .body("dataFiles[1].fileContent", equalTo("eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
    }

    @Test
    public void createAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileContent", startsWith(DEFAULT_DATAFILE_CONTENT))
                .body("dataFiles[1].fileName", equalTo("testFile.txt"))
                .body("dataFiles[1].fileContent", equalTo("eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
    }

    @Test
    public void createAsicContainerAndAddMultipleDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        JSONObject dataFiles = addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu");
        JSONObject dataFile = new JSONObject();
        dataFile.put("fileName", "testFile2.xml");
        dataFile.put("fileContent", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQgdG8gaGFuZGxlLg==");
        dataFiles.getJSONArray("dataFiles").put(dataFile);

        addDataFile(flow, dataFiles);

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileContent", startsWith(DEFAULT_DATAFILE_CONTENT))
                .body("dataFiles[1].fileName", equalTo("testFile.txt"))
                .body("dataFiles[1].fileContent", equalTo("eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"))
                .body("dataFiles[2].fileName", equalTo("testFile2.xml"))
                .body("dataFiles[2].fileContent", equalTo("eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQgdG8gaGFuZGxlLg=="));
    }

    @Test
    public void uploadSignedAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("valid.asice"));

        Response response = addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    public void deleteToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
