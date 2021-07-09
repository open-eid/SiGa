package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.AssumingProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class MobileSigningAsicContainerT extends TestBase {

    @ClassRule
    public static AssumingProfileActive assumingRule = new AssumingProfileActive("datafileContainer");

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void addSignatureToAsicContainerWithMidSuccessfully() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    public void signAsicContainerWithMidSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void signAsicContainerWithMidUser1PairOfRsaCertificatesSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("39901019992", "+37200001566", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void signAsicContainerWithMidUserOver21Successfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("45001019980", "+37200001466", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void signAsicContainerWithMidUserUnder18Successfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("61001019985", "+37200001366", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }
    @Ignore("Test uses Lithuanian test MID number for second signature.")
    @Test
    public void signWithMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response responseSigning1 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId1 = responseSigning1.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId1);

        Response responseSigning2 = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018865", "+37060000666", "LT"));
        String signatureId2 = responseSigning2.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    public void signWithMultipleSignaturesPerContainerInvalidAndValidSignature() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response responseSigning1 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019961", "+37200000666", "LT"));
        Response responseSigning2 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        String signatureId1 = responseSigning1.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId2 = responseSigning2.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        pollForMidSigning(flow, signatureId1);
        pollForMidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void mobileIdSendingFailed() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019947", "+37207110066", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SENDING_ERROR);
    }


    @Test
    public void mobileIdUserCancel() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, USER_CANCEL);
    }

    @Test
    public void mobileIdSignatureNotValid() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019961", "+37200000666", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, NOT_VALID);
    }

    @Test
    public void mobileIdSimError() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019972", "+37201200266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SIM_ERROR);
    }

    @Test
    public void mobileIdPhoneNotInNetwork() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019983", "+37213100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, PHONE_ABSENT);
    }

    @Test
    public void mobileIdUserTimeout() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, EXPIRED_TRANSACTION);
    }

    @Test
    public void mobileIdUserCancelAndRetries() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void mobileIdUserTimeoutsAndRetries() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void containerInSessionContainsEmptyDataFiles() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("unsignedContainerWithEmptyDatafiles.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    public void missingPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("", "+37200000766", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidPersonIdentifierFormat() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("P!NO-23a.31,23", "+37200000766", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void missingPhoneNumber() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidPhoneNumberFormat() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "-/ssasa", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void missingLanguageInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidLanguageInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "SOM", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void missingProfileInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidProfileInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "T", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void invalidRoleInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "LT", null, null, null, null, null, ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void maximumDataInRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "LT", "message", "Tallinn", "Harjumaa", "75544", "Estonia", "I hava a role"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void midStatusRequestForOtherUserContainer() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        response = getMidSigningInSession(flow, signatureId);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForMidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
        Response pollResponse = pollForMidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void deleteToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartAsicMidSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToAsicMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
