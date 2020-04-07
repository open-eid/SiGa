package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class UploadHashcodeContainerT extends TestBase {


    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerShouldReturnContainerId() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    @Ignore("Should manifest be required?")
    public void uploadHashcodeContainerWithoutManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingManifest.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerWithoutSha256hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha256File.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerWithoutSha512hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha512File.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    public void uploadHashcodeContainerWithDatafilesInFolder() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFolder.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerWithoutDatafiles() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutDataFiles.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerEmptyBody() throws Exception {
        JSONObject request = new JSONObject();
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadHashcodeContainerEmptyContainerField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "");

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadDDOCHashcodeContainer() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDdoc.ddoc"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void uploadDDOCContainer() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("container.ddoc"));

        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void uploadHashcodeContainerNotBase64Container() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadHashcodeContainerRandomStringAsContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));

        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadHashcodeContainerDatafilesSizeExceeded() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithBigHashcodesFile.asice"));
        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void deleteToUploadHashcodeContainer() throws Exception {
        Response response = delete(UPLOAD + HASHCODE_CONTAINERS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToUploadHashcodeContainer() throws Exception {
        Response response = put(UPLOAD + HASHCODE_CONTAINERS, flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER).toString());

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToUploadHashcodeContainer() throws Exception {
        Response response = get(UPLOAD + HASHCODE_CONTAINERS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToUploadHashcodeContainer() throws Exception {
        Response response = head(UPLOAD + HASHCODE_CONTAINERS, flow);

        response.then()
                .statusCode(405);
    }

    @Test
    public void optionsToUploadHashcodeContainer() throws Exception {
        Response response = options(UPLOAD + HASHCODE_CONTAINERS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToUploadHashcodeContainer() throws Exception {
        Response response = patch(UPLOAD + HASHCODE_CONTAINERS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void uploadHashcodeContainer_storedAlgoWithDataDescriptor() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeStoredAlgoWithDataDescriptor.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
