package ee.openeid.siga.test.hashcode;

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
import static ee.openeid.siga.test.helper.TestData.DATAFILES;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DUPLICATE_DATA_FILE;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_DATA;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.RESOURCE_NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.TEST_FILE_EXTENSIONS;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_FILENAME;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_SHA512_DATAFILE;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToHashcodeRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToHashcodeRequestDataFile;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFilesToHashcodeRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManipulateDataFilesHashcodeContainerT extends TestBase {
    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadHashcodeContainerAndRetrieveDataFilesList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(UPLOADED_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(UPLOADED_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(UPLOADED_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(UPLOADED_FILESIZE));
    }

    @Test
    void uploadHashcodeContainerWithoutSignaturesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(UPLOADED_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(UPLOADED_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(UPLOADED_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(UPLOADED_FILESIZE));
    }

    @Test
    void uploadHashcodeContainerWithInvalidSignatureAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidOcspValue.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(UPLOADED_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(UPLOADED_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(UPLOADED_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(UPLOADED_FILESIZE));
    }

    @Test
    void createHashcodeContainerAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    void createHashcodeContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

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
    void uploadHashcodeContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        response.then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles.size()", equalTo(1));
    }

    @Test
    void uploadHashcodeContainerAndRemoveNotExistingDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = deleteDataFile(flow, "random.txt");

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void uploadHashcodeContainerWithSignaturesAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[1].fileName"));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    void uploadHashcodeContainerWithSpecialCharactersAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeNonconventionalCharactersInDataFile.asice"));

        Response deleteResponse = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        deleteResponse.then().statusCode(200);

        Response response = getDataFileList(flow);
        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    //Some invalid chars (/, ?, \, \u0000) produce different errors as the filename is in URL and are excluded from test
    @ParameterizedTest(name = "Deleting datafile from hashcode container not allowed if fileName contains ''{0}''")
    @ValueSource(strings = {"`", "*", "<", ">", "|", "\"", ":", "\u0017", "\u0007"})
    void uploadHashcodeContainerAndTryRemovingDataFileWithInvalidFilename(String invalidChar) throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeNonconventionalCharactersInDataFile.asice"));
        Response response = deleteDataFile(flow, "Char=" + invalidChar + "isInvalid");

        expectError(response, 400, INVALID_REQUEST, "Data file name is invalid");
    }

    @Test
    void uploadHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[2].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[2].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[2].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[2].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    void uploadHashcodeContainerAndAddDuplicateDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = addDataFile(flow, addDataFileToHashcodeRequest("test.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadHashcodeContainerAndAddDataFileWithZeroFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = addDataFile(flow, addDataFileToHashcodeRequest("test.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, 0));

        expectError(response, 400, INVALID_REQUEST);
    }

    @ParameterizedTest(name = "Adding datafile to hashcode container not allowed if fileName contains ''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000", "\u0007"})
    void uploadHashcontainerAndTryAddingDataFileWithInvalidFilename(String invalidChar) throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = addDataFile(flow, addDataFileToHashcodeRequest("Char=" + invalidChar + ".txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        expectError(response, 400, INVALID_REQUEST, "Data file name is invalid");
    }

    @Test
    void createHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    void createHashcodeContainerAndAddMultipleDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject dataFiles = addDataFileToHashcodeRequest("testFile1.xml", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
        JSONObject dataFile = addDataFileToHashcodeRequestDataFile("testFile2.xml", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
        dataFiles.getJSONArray("dataFiles").put(dataFile);

        addDataFile(flow, dataFiles);

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE))
                .body("dataFiles[1].fileName", equalTo("testFile1.xml"))
                .body("dataFiles[1].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[1].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[1].fileSize", equalTo(DEFAULT_FILESIZE))
                .body("dataFiles[2].fileName", equalTo("testFile2.xml"))
                .body("dataFiles[2].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[2].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[2].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    void createHashcodeContainerAndAddDuplicateDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void createHashcodeContainerAndAddMultipleDataFileMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        addDataFile(flow, addDataFilesToHashcodeRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> addDataFileToHashcodeRequestDataFile("filename." + ext, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE))
                .collect(Collectors.toList())));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (2 + i) + "].@manifest:media-type"));
        }
    }

    @Test
    void createHashcodeContainerAndAddDataFileWithZeroFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = addDataFile(flow, addDataFileToHashcodeRequest("test.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, 0));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadSignedHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    void headToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
