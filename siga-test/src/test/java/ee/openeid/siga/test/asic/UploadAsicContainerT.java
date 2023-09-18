package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.CONTAINER_ID;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DUPLICATE_DATA_FILE;
import static ee.openeid.siga.test.helper.TestData.INVALID_CONTAINER;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.UPLOAD;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static org.hamcrest.CoreMatchers.equalTo;

@EnabledIfSigaProfileActive("datafileContainer")
class UploadAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadAsicContainerShouldReturnContainerId() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    @Disabled("Should manifest be required?")
    void uploadAsicContainerMissingManifest() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerMissingManifest.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadAsicContainerWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadAsicContainerWithEmptyDataFiles() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("signedContainerWithEmptyDatafiles.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadAsicContainerWithEmptyDataFilesAndWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("unsignedContainerWithEmptyDatafiles.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadAsicContainerWithoutDatafiles() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerNoDataFile.bdoc"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadDdocContainer() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("ddocSingleSignature.ddoc"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadPadesContainer() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("pdfSingleSignature.pdf"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadAsicContainerEmptyBody() throws Exception {
        JSONObject request = new JSONObject();
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadAsicContainerEmptyContainerField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "");
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadAsicContainerEmptyContainerNameField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("containerName", "");
        request.put("container", "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @ParameterizedTest(name = "Uploading ASIC container not allowed if containerName contains ''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000", "\u0007"})
    void tryUploadingAsicContainerWithInvalidFileName(String fileName) throws Exception {
        JSONObject request = new JSONObject();
        request.put("containerName", "InvalidChar=" + fileName);
        request.put("container", "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST, "Container name is invalid");
    }

    @Test
    void uploadAsiceContainerNotBase64Container() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadAsicContainerRandomStringAsContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void deleteToUploadAsicContainer() throws Exception {
        Response response = delete(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void putToUploadAsicContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("containerName", "container.asice");
        request.put("container", "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=");

        Response response = put(UPLOAD + getContainerEndpoint(), flow, request.toString());

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void getToUploadAsicContainer() throws Exception {
        Response response = get(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToUploadAsicContainer() throws Exception {
        Response response = head(UPLOAD + getContainerEndpoint(), flow);

        response.then()
                .statusCode(405);
    }

    @Test
    void optionsToUploadAsicContainer() throws Exception {
        Response response = options(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void patchToUploadAsicContainer() throws Exception {
        Response response = patch(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void uploadContainerWithDuplicateDataFiles() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("asice_duplicate_data_files.asice"));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadContainerWithDuplicateDataFileInManifest() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("asice_duplicate_data_files_in_manifest.asice"));

        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
