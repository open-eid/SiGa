package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_HASHCODE_CONTAINER;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.EXPIRED_TRANSACTION;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.INVALID_SESSION_DATA_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.MID_SIGNING;
import static ee.openeid.siga.test.helper.TestData.NOT_VALID;
import static ee.openeid.siga.test.helper.TestData.PHONE_ABSENT;
import static ee.openeid.siga.test.helper.TestData.RESOURCE_NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.SENDING_ERROR;
import static ee.openeid.siga.test.helper.TestData.SERVICE_SECRET_2;
import static ee.openeid.siga.test.helper.TestData.SERVICE_UUID_2;
import static ee.openeid.siga.test.helper.TestData.STATUS;
import static ee.openeid.siga.test.helper.TestData.USER_CANCEL;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToHashcodeRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.midSigningRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.midSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.midSigningRequestWithMessageToDisplay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSigaProfileActive("mobileId")
class MobileSigningHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void addSignatureToHashcodeContainerWithMidSuccessfully() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    void signHashcodeContainerWithMidSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signHashcodeContainerWithMidUser1PairOfRsaCertificatesSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("39901019992", "+37200001566", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signHashcodeContainerWithMidUserOver21Successfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("45001019980", "+37200001466", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signHashcodeContainerWithMidUserUnder18Successfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("61001019985", "+37200001366", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signWithMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response responseSigning1 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        Response responseSigning2 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001018800", "+37200000566", "LT"));

        String signatureId1 = responseSigning1.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId2 = responseSigning2.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        pollForMidSigning(flow, signatureId1);
        pollForMidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    void signWithMultipleSignaturesPerContainerInvalidAndValidSignature() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response responseSigning1 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019961", "+37200000666", "LT"));
        Response responseSigning2 = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        String signatureId1 = responseSigning1.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId2 = responseSigning2.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        pollForMidSigning(flow, signatureId1);
        pollForMidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void mobileIdSendingFailed() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019947", "+37207110066", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SENDING_ERROR);
    }

    @Test
    void mobileIdInvalidInternationalCallingCode() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019947", "+37107110066", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void mobileIdUserCancel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, USER_CANCEL);
    }

    @Test
    void mobileIdSignatureNotValid() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019961", "+37200000666", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, NOT_VALID);
    }

    @Test
    void mobileIdSimError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019972", "+37201200266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, SENDING_ERROR);
    }

    @Test
    void mobileIdPhoneNotInNetwork() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019983", "+37213100266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, PHONE_ABSENT);
    }

    @Test
    void mobileIdUserTimeout() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        response = pollForMidSigning(flow, signatureId);

        expectMidStatus(response, EXPIRED_TRANSACTION);
    }

    @Test
    void mobileIdUserCancelAndRetries() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019950", "+37201100266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void mobileIdUserTimeoutsAndRetries() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("50001018908", "+37066000266", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void containerInSessionContainsEmptyDataFiles() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @ParameterizedTest(name = "Starting MID signing hashcode container successful if messageToDisplay field contains char''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0000", "\u0007"})
    void signHashcodeContainerWithSpecialCharsInMessageToDisplaySuccessful(String specialChar) throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        String messageToDisplay = "Special char = " + specialChar;
        Response response = postMidSigningInSession(flow, midSigningRequestWithMessageToDisplay("60001019906", "+37200000766", messageToDisplay));

        response.then().statusCode(200);
    }

    @Test
    void missingPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("", "+37200000766", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void invalidPersonIdentifierFormat() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("P!NO-23a.31,23", "+37200000766", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void missingPhoneNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void invalidPhoneNumberFormat() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "-/ssasa", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void missingLanguageInRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void invalidLanguageInRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "SOM", "LT", null, null, null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void invalidRoleInRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "LT", null, null, null, null, null, ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Mobile signing new hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void signingNewHashcodeContainerWithMidInvalidSignatureProfileNotAllowed(String signatureProfile) throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Mobile signing uploaded hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void signingUploadedHashcodeContainerWithMidInvalidSignatureProfileNotAllowed(String signatureProfile) throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @Test
    void maximumDataInRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequest("60001019906", "+37200000766", "EST", "LT", "message", "Tallinn", "Harjumaa", "75544", "Estonia", "I hava a role"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void midStatusRequestForOtherUserContainer() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        flow.setServiceUuid(SERVICE_UUID_2);
        flow.setServiceSecret(SERVICE_SECRET_2);
        response = getMidSigningInSession(flow, signatureId);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForMidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        Response pollResponse = pollForMidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void headToHashcodeMidSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = startResponse.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + MID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
