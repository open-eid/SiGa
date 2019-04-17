package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static org.hamcrest.CoreMatchers.equalTo;

public class DeleteHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndDelete() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void createHashcodeContainerAndDelete() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAlwaysReturnsOk() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteHashcodeContainer(flow);

        Response response = deleteHashcodeContainer(flow);

        response.then()
                .statusCode(200)
                .body(RESULT, equalTo("OK"));
    }

    @Test
    public void deleteHashcodeContainerBeforeSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingIt() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getBody().path(DATA_TO_SIGN), dataToSignResponse.getBody().path(DIGEST_ALGO))));
        getHashcodeContainer(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerBeforeFinishingMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        deleteHashcodeContainer(flow);

        Response response = getHashcodeMidSigningInSession(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerDuringMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        getHashcodeMidSigningInSession(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeMidSigningInSession(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterMidSigning() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        pollForMidSigning(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterValidation() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        deleteHashcodeContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingSignatures() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getHashcodeSignatureList(flow);

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void deleteHashcodeContainerForOtherClientNotPossible() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        deleteHashcodeContainer(flow);

        flow.setServiceUuid(SERVICE_UUID_1);
        flow.setServiceSecret(SERVICE_SECRET_1);

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"));
    }

    @Test
    public void postToDeleteHashcodeContainer() throws Exception {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = post(HASHCODE_CONTAINERS + "/" + flow.getContainerId(), flow, "");

        response.then()
                .statusCode(405)
                .body(ERROR_CODE, equalTo(INVALID_REQUEST));
    }
}
