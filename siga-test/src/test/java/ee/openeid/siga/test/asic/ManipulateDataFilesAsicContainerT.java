package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import eu.europa.esig.dss.model.MimeType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.CONTAINER;
import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.DATAFILES;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_DATAFILE_CONTENT;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DUPLICATE_DATA_FILE;
import static ee.openeid.siga.test.helper.TestData.INVALID_DATA;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.RESOURCE_NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.TEST_FILE_EXTENSIONS;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_FILENAME;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToAsicRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToAsicRequestDataFile;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFilesToAsicRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSigaProfileActive("datafileContainer")
class ManipulateDataFilesAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadAsicContainerAndRetrieveDataFilesList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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
    void uploadAsicContainerWithoutSignaturesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo("test.txt"))
                .body("dataFiles[0].fileContent", equalTo("c2VlIG9uIHRlc3RmYWls"));
    }

    @Test
    void uploadAsicContainerWithoutDataFilesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutDataFiles.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Test
    void uploadAsicContainerWithInvalidSignatureAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknownOcspResponder.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo("test.txt"))
                .body("dataFiles[0].fileContent", equalTo("MTIzCg=="));
    }

    @Test
    void createAsicContainerAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileContent", equalTo(DEFAULT_DATAFILE_CONTENT));
    }

    @Test
    void createAsicContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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

    @Test
    void uploadAsicContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

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
    void uploadAsicContainerAndRemoveNotExistingDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = deleteDataFile(flow, "random.txt");

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void uploadAsicContainerWithSignaturesAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[1].fileName"));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    void uploadAsicContainerWithSpecialCharactersAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("NonconventionalCharactersInDataFile.asice"));

        Response deleteResponse = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        deleteResponse.then().statusCode(200);

        Response response = getDataFileList(flow);
        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    //Some invalid chars (/, ?, \, \u0000) produce different errors as the filename is in URL and are excluded from test
    @ParameterizedTest(name = "Deleting datafile from ASIC container not allowed if fileName contains ''{0}''")
    @ValueSource(strings = {"`", "*", "<", ">", "|", "\"", ":", "\u0017", "\u0007"})
    void uploadAsicContainerAndTryRemovingDataFileWithInvalidFilename(String invalidChar) throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        Response response = deleteDataFile(flow, "Char=" + invalidChar + "isInvalid");

        expectError(response, 400, INVALID_REQUEST, "Data file name is invalid");
    }

    @Test
    void uploadAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(UPLOADED_FILENAME))
                .body("dataFiles[0].fileContent", startsWith("c2VlIG9uIHRlc3RmYWls"))
                .body("dataFiles[1].fileName", equalTo("testFile.txt"))
                .body("dataFiles[1].fileContent", equalTo("eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
    }

    @Test
    void uploadAsicContainerAndAddDuplicateDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
        Response response = addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadAsicContainerAndAddEmptyDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = addDataFile(flow, addDataFileToAsicRequest("testFile.txt", ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @ParameterizedTest(name = "Adding datafile to ASIC container not allowed if fileName contains ''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000", "\u0007"})
    void uploadAsicContainerAndTryAddingDataFileWithInvalidFilename(String invalidChar) throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = addDataFile(flow, addDataFileToAsicRequest("Char=" + invalidChar + ".txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        expectError(response, 400, INVALID_REQUEST, "Data file name is invalid");
    }

    @Test
    void createAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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
    void createAsicContainerAndAddDuplicateDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = addDataFile(flow, addDataFileToAsicRequest(DEFAULT_FILENAME, "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void createAsicContainerAndAddMultipleDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
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
    void createAsicContainerAndAddMultipleDataFileMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        addDataFile(flow, addDataFilesToAsicRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> addDataFileToAsicRequestDataFile("filename." + ext, DEFAULT_DATAFILE_CONTENT))
                .collect(Collectors.toList())));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (2 + i) + "].@manifest:media-type"));
        }
    }

    @Test
    void createAsicContainerAndAddEmptyDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = addDataFile(flow, addDataFileToAsicRequest("testFile.txt", ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadSignedAsicContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    void deleteToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void putToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    void optionsToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToAsicDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void getToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void putToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void postToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void optionsToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToAsicDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
