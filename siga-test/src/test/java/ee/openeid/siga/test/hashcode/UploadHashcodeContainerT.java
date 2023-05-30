package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class UploadHashcodeContainerT extends TestBase {


    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadHashcodeContainerShouldReturnContainerId() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadHashcodeContainerWithoutManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingManifest.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerWithoutSha256hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha256File.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerWithoutSha512hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha512File.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadHashcodeContainerWithEmptyDataFiles() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeSignedContainerWithEmptyDatafiles.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadHashcodeContainerWithEmptyDataFilesAndWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadHashcodeContainerWithDatafilesInFolder() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFolder.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerWithoutDatafilesInDatafileXml() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutDataFiles.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerEmptyBody() throws Exception {
        JSONObject request = new JSONObject();
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadHashcodeContainerEmptyContainerField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "");

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadDDOCHashcodeContainer() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDdoc.ddoc"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    void uploadDDOCContainer() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("container.ddoc"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    void uploadHashcodeContainerNotBase64Container() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadHashcodeContainerRandomStringAsContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainerDatafilesSizeExceeded() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithBigHashcodesFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadHashcodeContainer_storedAlgoWithDataDescriptor() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeStoredAlgoWithDataDescriptor.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void uploadContainerWithDuplicateDataFiles() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode_duplicate_data_files.asice"));
        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadContainerWithDuplicateDataFileInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode_duplicate_data_files_in_manifest.asice"));
        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadContainerWithDuplicateDataFilesInSignature() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode_duplicate_data_files_in_signature.asice"));
        expectError(response, 400, DUPLICATE_DATA_FILE);
    }

    @Test
    void uploadContainerWithInvalidStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcode_invalid_structure.asice"));
        expectError(response, 400, INVALID_CONTAINER_EXCEPTION);
    }

    @Test
    void uploadContainerWithInvalidDatafileXmlStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWrongFileStructureInDatafileDescriptorFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadContainerWithInvalidBase64InDatafileXmlStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidBase64InDatafileDescriptorFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadContainerWithInvalidDatafileSizeInDatafileXmlStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidDatafileSizeInDatafileDescriptorFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    void uploadContainerWithInvalidBase64LengthInDatafileXmlStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidBase64LengthInDatafileDescriptorFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Disabled("SIGA-264")
    @Test
    void uploadContainerWithExtraDatafileInDatafileXmlStructure() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeNotSignedDatafilesInDatafileDescriptorFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
