package ee.openeid.siga.test.asic;

import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.RESOURCE_NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.RESULT;
import static ee.openeid.siga.test.helper.TestData.SERVICE_SECRET_1;
import static ee.openeid.siga.test.helper.TestData.SERVICE_SECRET_2;
import static ee.openeid.siga.test.helper.TestData.SERVICE_UUID_1;
import static ee.openeid.siga.test.helper.TestData.SERVICE_UUID_2;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.midSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.CoreMatchers.equalTo;

@EnabledIfSigaProfileActive("datafileContainer")
class DeleteAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadAsicContainerAndDelete() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void createAsicContainerAndDelete() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerAlwaysReturnsOk() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        deleteContainer(flow);

        Response response = deleteContainer(flow);

        response.then()
                .statusCode(200)
                .body(RESULT, equalTo(Result.OK.name()));
    }

    @Test
    void deleteAsicContainerBeforeSigning() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerAfterSigning() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerAfterRetrievingIt() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        getContainer(flow);
        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void deleteAsicContainerBeforeFinishingMidSigning() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        deleteContainer(flow);

        response = getMidSigningInSession(flow, signatureId);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void deleteAsicContainerDuringMidSigning() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        getMidSigningInSession(flow, signatureId);

        deleteContainer(flow);

        response = getMidSigningInSession(flow, signatureId);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void deleteAsicContainerAfterMidSigning() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        deleteContainer(flow);

        response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerAfterValidation() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        getValidationReportForContainerInSession(flow);

        deleteContainer(flow);

        Response response = getValidationReportForContainerInSession(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerAfterRetrievingSignatures() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        getSignatureList(flow);

        deleteContainer(flow);

        Response response = getSignatureList(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void deleteAsicContainerForOtherClientNotPossible() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        deleteContainer(flow);

        flow.setServiceUuid(SERVICE_UUID_1);
        flow.setServiceSecret(SERVICE_SECRET_1);

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE"));
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
