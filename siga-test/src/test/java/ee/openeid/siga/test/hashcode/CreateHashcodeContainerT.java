package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import eu.europa.esig.dss.model.MimeType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
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
        Response response = postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(CONTAINER_ID).toString().length(), equalTo(36));
    }

    @Test
    public void createHashcodeContainerMimeTypeFromFileExtension() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequest(TEST_FILE_EXTENSIONS.stream()
                .map(ext -> hashcodeContainersDataRequestDataFile("filename." + ext, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE))
                .collect(Collectors.toList())));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, getContainer(flow).getBody().path(CONTAINER).toString()));
        for (int i = 0; i < TEST_FILE_EXTENSIONS.size(); ++i) {
            String expectedMimeType = MimeType.fromFileName("*." + TEST_FILE_EXTENSIONS.get(i)).getMimeTypeString();
            Assert.assertEquals(expectedMimeType, manifest.getString("manifest:manifest.manifest:file-entry[" + (1 + i) + "].@manifest:media-type"));
        }
    }

    @Test
    public void createHashcodeContainerEmptyBody() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject request = new JSONObject();
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptyDatafiles() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONArray datafiles = new JSONArray();
        JSONObject request = new JSONObject();
        request.put("dataFiles", datafiles);
        Response response = postCreateContainer(flow, request);
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
        Response response = postCreateContainer(flow, request);
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
        Response response = postCreateContainer(flow, request);
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
        Response response = postCreateContainer(flow, request);
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
        Response response = postCreateContainer(flow, request);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_REQUEST));
    }

    @Test
    public void createHashcodeContainerEmptyFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest("", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerEmptySha256Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, " ", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerEmptySha512Hash() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "    ", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerEmptyFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, null));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerInvalidFileName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest("?%*", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerFileInFolder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest("folder/test.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerInvalidFileSize() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, -12));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerInvalidHash256() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, "+-KZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerInvalidHash512() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, "+-Vz9wirVZNvP/q3HoaW8nu0FfvrGkZinhADKE4Y4j/dUuGfgONfR4VYdu0p/dj/yGH0qlE0FGsmUB2N3oLuhA==", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerTooLongHash256length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE + "=", DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerTooShortHash256length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE.substring(1), DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerTooLongHash512length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE + "=", DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void createHashcodeContainerTooShortHash512length() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE.substring(1), DEFAULT_FILESIZE));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
