package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrieveHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadHashcodeContainerAndRetrieveIt() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19680));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, response.path(CONTAINER).toString()));

        assertEquals("text/plain", manifest.getString("manifest:manifest.manifest:file-entry[" + (2) + "].@manifest:media-type"));
    }

    @Test
    void createHashcodeContainerAndRetrieve() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(1400));

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, response.path(CONTAINER).toString()));

        assertEquals("text/plain", manifest.getString("manifest:manifest.manifest:file-entry[" + (1) + "].@manifest:media-type"));
    }

    @Test
    void retrieveHashcodeContainerTwice() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(1400));

        response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(1400));
    }

    @Test
    void retrieveHashcodeContainerBeforeSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(19000));
    }

    @Test
    void retrieveHashcodeContainerAfterSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(37000));
    }

    @Test
    void retrieveHashcodeContainerBeforeFinishingMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19684));
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void retrieveHashcodeContainerDuringMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        getMidSigningInSession(flow, signatureId);

        response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19684));
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void retrieveHashcodeContainerAfterMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(35000));
    }

    @Test
    void retrieveHashcodeContainerAfterValidation() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19684));
    }

    @Test
    void retrieveHashcodeContainerAfterRetrievingSignatures() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getSignatureList(flow);

        Response response = getContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19684));
    }

    @Test
    void retrieveHashcodeContainerForOtherClientNotPossible() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        Response response = getContainer(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteHashcodeContainerAndRetrieveIt() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteContainer(flow);

        Response response = getContainer(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void headToGetHashcodeContainer() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId(), flow);

        response.then()
                .statusCode(200);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
