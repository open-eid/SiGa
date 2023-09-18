package ee.openeid.siga.test.hashcode;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.CONTAINER;
import static ee.openeid.siga.test.helper.TestData.CONTAINER_ID;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.ERROR_CODE;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.TEST_FILE_EXTENSIONS;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestDataFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void createHashcodeContainerShouldReturnContainerId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    void createHashcodeContainerMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> hashcodeContainersDataRequestDataFile("filename." + ext, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE))
                .collect(Collectors.toList())));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (1 + i) + "].@manifest:media-type"));
        }
    }

    @Test
    void createHashcodeContainerEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerEmptyDatafiles() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject request = new JSONObject();
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerMissingFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerMissingSha256Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerMissingSha512Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileSize", DEFAULT_FILESIZE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerMissingFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", DEFAULT_FILENAME);
        dataFileObject.put("fileHashSha256", DEFAULT_SHA256_DATAFILE);
        dataFileObject.put("fileHashSha512", DEFAULT_SHA512_DATAFILE);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    void createHashcodeContainerEmptyFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest("", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerEmptySha256Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, " ", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerEmptySha512Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "    ", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerEmptyFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, null));
        expectError(response, 400, INVALID_REQUEST);
    }

    @ParameterizedTest(name = "Creating hashcode container not allowed if fileName contains ''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000", "\u0007"})
    void tryCreatingHashcodeContainerWithInvalidFileName(String fileName) throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(fileName, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST, "Data file name is invalid");
    }

    @Test
    void createHashcodeContainerWithSpecialCharsInFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        String fileName = "!#$%&'()+,-.0123456789;=@ ABCDEFGHIJKLMNOPQRSTUVWXYZÕÄÖÜ[]^_abcdefghijklmnopqrstuvwxyzõäöü{}~";
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(fileName, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        response.then().statusCode(200);
    }

    @Test
    void createHashcodeContainerFileInFolder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest("folder/test.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerInvalidFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, -12));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerZeroFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, 0));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerInvalidHash256() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, "+-KZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerInvalidHash512() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "+-Vz9wirVZNvP/q3HoaW8nu0FfvrGkZinhADKE4Y4j/dUuGfgONfR4VYdu0p/dj/yGH0qlE0FGsmUB2N3oLuhA==", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerTooLongHash256length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE + "=", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerTooShortHash256length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE.substring(1), DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerTooLongHash512length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE + "=", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void createHashcodeContainerTooShortHash512length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE.substring(1), DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
