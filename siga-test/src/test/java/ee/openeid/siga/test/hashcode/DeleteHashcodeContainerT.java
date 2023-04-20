package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;

public class DeleteHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndDelete() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void createHashcodeContainerAndDelete() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAlwaysReturnsOk() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        deleteContainer(flow);

        Response response = deleteContainer(flow);

        response.then()
                .statusCode(200)
                .body(RESULT, equalTo(Result.OK.name()));
    }

    @Test
    public void deleteHashcodeContainerBeforeSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAfterSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingIt() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        getContainer(flow);
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    public void deleteHashcodeContainerBeforeFinishingMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        deleteContainer(flow);

        response = getMidSigningInSession(flow, signatureId);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    public void deleteHashcodeContainerDuringMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        getMidSigningInSession(flow, signatureId);

        deleteContainer(flow);

        response = getMidSigningInSession(flow, signatureId);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    public void deleteHashcodeContainerAfterMidSigning() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        deleteContainer(flow);

        response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAfterValidation() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getValidationReportForContainerInSession(flow);

        deleteContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerAfterRetrievingSignatures() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        getSignatureList(flow);

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void deleteHashcodeContainerForOtherClientNotPossible() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        deleteContainer(flow);

        flow.setServiceUuid(SERVICE_UUID_1);
        flow.setServiceSecret(SERVICE_SECRET_1);

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
