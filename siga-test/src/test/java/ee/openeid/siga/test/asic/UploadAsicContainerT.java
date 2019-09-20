package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.AssumingProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static org.hamcrest.CoreMatchers.equalTo;

public class UploadAsicContainerT extends TestBase {

    @ClassRule
    public static AssumingProfileActive assumingRule = new AssumingProfileActive("datafileContainer");

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadAsicContainerShouldReturnContainerId() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    @Ignore("Should manifest be required?")
    public void uploadAsicContainerMissingManifest() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerMissingManifest.asice"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadAsicContainerWithoutSignatures() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.bdoc"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    public void uploadAsicContainerWithoutDatafiles() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("containerNoDataFile.bdoc"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadAsicContainerWithDdoc() throws Exception {
        Response response = postUploadContainer(flow, asicContainerRequestFromFile("ddocSingleSignature.ddoc"));

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void uploadAsicContainerEmptyBody() throws Exception {
        JSONObject request = new JSONObject();
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadAsicContainerEmptyContainerField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "");
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadAsicContainerEmptyContainerNameField() throws Exception {
        JSONObject request = new JSONObject();
        request.put("containerName", "");
        request.put("container", "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadAsiceContainerNotBase64Container() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", "-32/432+*");
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadAsicContainerRandomStringAsContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("container", Base64.encodeBase64String("random string".getBytes()));
        request.put("containerName", "container.asice");
        Response response = postUploadContainer(flow, request);

        expectError(response, 400, INVALID_CONTAINER);
    }

    @Test
    public void deleteToUploadAsicContainer() throws Exception {
        Response response = delete(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToUploadAsicContainer() throws Exception {
        JSONObject request = new JSONObject();
        request.put("containerName", "container.asice");
        request.put("container", "RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=");

        Response response = put(UPLOAD + getContainerEndpoint(), flow, request.toString());

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToUploadAsicContainer() throws Exception {
        Response response = get(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToUploadAsicContainer() throws Exception {
        Response response = head(UPLOAD + getContainerEndpoint(), flow);

        response.then()
                .statusCode(405);
    }

    @Test
    public void optionsToUploadAsicContainer() throws Exception {
        Response response = options(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToUploadAsicContainer() throws Exception {
        Response response = patch(UPLOAD + getContainerEndpoint(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
