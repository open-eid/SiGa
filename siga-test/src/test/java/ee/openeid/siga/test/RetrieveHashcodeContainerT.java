package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.*;

public class RetrieveHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveIt() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19660));
    }

    @Test
    public void createHashcodeContainerAndRetrieve() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(1400));
    }

    @Test
    public void retrieveHashcodeContainerTwice() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(1440));

        response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(1440));
    }

    @Test
    public void retrieveHashcodeContainerBeforeSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(19000));
    }

    @Test
    public void retrieveHashcodeContainerAfterSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(37000));
    }

    @Test
    public void retrieveHashcodeContainerBeforeFinishingMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerDuringMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        getHashcodeMidSigningInSession(flow, signatureId);

        response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerAfterMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", greaterThan(35000));
    }

    @Test
    public void retrieveHashcodeContainerAfterValidation() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerAfterRetrievingSignatures() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getHashcodeSignatureList(flow);

        Response response = getHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(CONTAINER + ".length()", equalTo(19660));
    }

    @Test
    public void retrieveHashcodeContainerForOtherClientNotPossible() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        Response response = getHashcodeContainer(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAndRetrieveIt() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteHashcodeContainer(flow);

        Response response = getHashcodeContainer(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void postToGetHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = post(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow, "");
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToGetHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = head(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);

        response.then()
                .statusCode(200);
    }

    @Ignore ("SIGARIA-67")
    @Test
    public void optionsToGetHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = options(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToGetHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = patch(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow);
        expectError(response, 405, INVALID_REQUEST);
    }
}
