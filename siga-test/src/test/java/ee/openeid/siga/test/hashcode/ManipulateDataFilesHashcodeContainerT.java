package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import eu.europa.esig.dss.model.MimeType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ManipulateDataFilesHashcodeContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveDataFilesList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    public void uploadHashcodeContainerWithoutSignaturesAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    public void uploadHashcodeContainerWithInvalidSignatureAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidOcspValue.asice"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[0].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[0].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[0].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    public void createHashcodeContainerAndRetrieveDataFileList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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
    public void createHashcodeContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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
    public void uploadHashcodeContainerAndRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
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
    public void uploadHashcodeContainerAndRemoveNotExistingDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = deleteDataFile(flow, "random.txt");

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void uploadHashcodeContainerWithSignaturesAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[1].fileName"));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    public void uploadHashcodeContainerWithSpecialCharactersAndTryToRemoveDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeNonconventionalCharactersInDataFile.asice"));

        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        Response response = getDataFileList(flow);

        response.then()
                .statusCode(200)
                .body("dataFiles[0]", nullValue());
    }

    @Test
    public void uploadHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

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
    public void createHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException  {
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
    public void createHashcodeContainerAndAddMultipleDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException  {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject dataFiles = addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
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
                .body("dataFiles[1].fileName", equalTo(DEFAULT_FILENAME))
                .body("dataFiles[1].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[1].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[1].fileSize", equalTo(DEFAULT_FILESIZE))
                .body("dataFiles[2].fileName", equalTo("testFile2.xml"))
                .body("dataFiles[2].fileHashSha256", equalTo(DEFAULT_SHA256_DATAFILE))
                .body("dataFiles[2].fileHashSha512", equalTo(DEFAULT_SHA512_DATAFILE))
                .body("dataFiles[2].fileSize", equalTo(DEFAULT_FILESIZE));
    }

    @Test
    public void createHashcodeContainerAndAddMultipleDataFileMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        addDataFile(flow, addDataFilesToHashcodeRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> addDataFileToHashcodeRequestDataFile("filename." + ext, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE))
                .collect(Collectors.toList())));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            Assert.assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (2 + i) + "].@manifest:media-type"));
        }
    }

    @Test
    public void uploadSignedHashcodeContainerAndAddDataFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));

        expectError(response, 400, INVALID_DATA);
    }

    @Test
    public void deleteToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToHashcodeDataFilesList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToHashcodeDataFile() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode.asice"));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + DATAFILES + "/" + getDataFileList(flow).getBody().path("dataFiles[0].fileName"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
