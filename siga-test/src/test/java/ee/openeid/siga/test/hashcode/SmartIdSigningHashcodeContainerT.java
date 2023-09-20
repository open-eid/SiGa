package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdCertificateChoiceStatusResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.CERTIFICATE_CHOICE;
import static ee.openeid.siga.test.helper.TestData.CLIENT_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_HASHCODE_CONTAINER;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.EXPIRED_TRANSACTION;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_DATA;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.INVALID_SESSION_DATA_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.SID_EE_DEFAULT_DOCUMENT_NUMBER;
import static ee.openeid.siga.test.helper.TestData.SID_EE_MULT_ACCOUNTS_DOCUMENT_NUMBER;
import static ee.openeid.siga.test.helper.TestData.SMARTID_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.SMARTID_SIGNING;
import static ee.openeid.siga.test.helper.TestData.STATUS;
import static ee.openeid.siga.test.helper.TestData.USER_CANCEL;
import static ee.openeid.siga.test.helper.TestData.USER_SELECTED_WRONG_VC;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToHashcodeRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.smartIdCertificateChoiceRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.smartIdSigningRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.smartIdSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.smartIdSigningRequestWithMessageToDisplay;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSigaProfileActive("smartId")
class SmartIdSigningHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void signWithSmartIdWithCertificateChoiceSuccessfullyEstonia() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Disabled
    @Test
    void signWithSmartIdWithCertificateChoiceSuccessfullyLatvia() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("030303-10012", "LV"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Disabled
    @Test
    void signWithSmartIdWithCertificateChoiceSuccessfullyLithuania() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "LT"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signWithSmartIdCertificateChoiceMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response certificateChoice1 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId1 = certificateChoice1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId1);

        String documentNumber1 = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingRequest1 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber1));
        String signatureId1 = signingRequest1.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId1);

        Response certificateChoice2 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId2 = certificateChoice2.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId2);

        String documentNumber2 = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingRequest2 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber2));
        String signatureId2 = signingRequest2.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    void signWithSmartIdCertificateChoiceRetryAfterUserCancel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response certificateChoice1 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30403039917", "EE"));
        String generatedCertificateId1 = certificateChoice1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId1);

        String documentNumber1 = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingRequest1 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber1));
        String signatureId1 = signingRequest1.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId1);

        Response certificateChoice2 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId2 = certificateChoice2.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId2);

        String documentNumber2 = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingRequest2 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber2));
        String signatureId2 = signingRequest2.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void smartIdSigningStatusRequestAfterSuccessfulFinalization() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response signAfterFinalizationResponse = getSmartIdSigningInSession(flow, generatedSignatureId);

        expectError(signAfterFinalizationResponse, 400, INVALID_DATA);
    }

    @Test
    void postWithSmartIdCertificateChoiceContainerInSessionContainsEmptyDataFiles() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));
        Response response = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    void postWithSmartIdCertificateChoiceSymbolsInPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest(".!:", "EE"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void postWithSmartIdCertificateChoiceInvalidFormatPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("39101290235", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    void postWithSmartIdCertificateChoiceMissingPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("country", "EE");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void postWithSmartIdCertificateChoiceMissingCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "30303039914");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Disabled
    @Test
    void postWithSmartIdCertificateChoicePersonIdentifierCountryMismatch() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "30403039917");
        request.put("country", "LV");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, SMARTID_EXCEPTION);
    }

    @Test
    void postWithSmartIdCertificateChoiceEmptyPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("", "EE"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void postWithSmartIdCertificateChoiceInvalidCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "ee"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void getSmartIdCertificateChoiceInvalidCertificateId() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        Response response = pollForSidCertificateStatus(flow, "00000000-0000-0000-0000-000000000000");

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void signSmartIdContainerInSessionContainsEmptyDataFiles() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));

        expectError(signingResponse, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    void signSmartIdInvalidFormatDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE.30303039914-Z1B2-Q"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void signSmartIdEmptyDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void signSmartIdMissingDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @ParameterizedTest(name = "Starting SID signing hashcode container successful if messageToDisplay field contains char''{0}''")
    @ValueSource(strings = {"/", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "\u0017", "\u0007"})
    void signSmartIdHashcodeContainerInSessionWithSpecialCharsInMessageToDisplaySuccessful(String specialChar) throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        String messageToDisplay = "Special char = " + specialChar;
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithMessageToDisplay(SID_EE_DEFAULT_DOCUMENT_NUMBER, messageToDisplay));

        signingResponse.then().statusCode(200);
    }

    @Test
    void signSmartIdHashcodeContainerInSessionWithSpecialCharNullInMessageToDisplayFails() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String messageToDisplay = "Special char = " + "\u0000";
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithMessageToDisplay(SID_EE_DEFAULT_DOCUMENT_NUMBER, messageToDisplay));

        expectError(signingResponse, 400, CLIENT_EXCEPTION, "Smart-ID service error");
    }

    @Test
    void getSmartIdSidStatusCertificateReturned() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response certificateStatus = pollForSidCertificateStatus(flow, generatedCertificateId);
        String sidCertificateStatus = certificateStatus.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getSidStatus();

        assertThat(sidCertificateStatus, is("CERTIFICATE"));
    }

    @Test
    void getSmartIdSidStatusErrorOnSecondRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId);
        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);

        expectError(certificateStatus, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Disabled
    @Test
    void smartIdCertificateChoiceAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101020001", "LT"));

        expectError(certificateChoice, 400, CLIENT_EXCEPTION);
    }

    @Test
    void signWithSmartIdSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signWithSmartIdSuccessfullyUserHasOtherActiveAccount() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_MULT_ACCOUNTS_DOCUMENT_NUMBER));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    void signWithSmartIdMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response signingRequest1 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        Response signingRequest2 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        String signatureId1 = signingRequest1.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId2 = signingRequest2.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId1);
        pollForSidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2));
    }

    @Test
    void signWithSmartIdRetryAfterUserCancel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response signingRequest1 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039917-905H-Q"));
        Response signingRequest2 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        String signatureId1 = signingRequest1.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        String signatureId2 = signingRequest2.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId1);
        pollForSidSigning(flow, signatureId2);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Disabled
    @Test
    void signWithSmartIdAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOLT-10101020001-K87V-NQ"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Smart-ID signing new hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void createNewHashcodeSidContainerWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault(signatureProfile, "PNOEE-30403039917-905H-Q"));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Smart-ID signing uploaded hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void uploadHashcodeSidSigningContainerWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault(signatureProfile, "PNOEE-30403039917-905H-Q"));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @Test
    void signWithSmartIdUserRefused() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039917-905H-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserRefusedDisplayTextAndPin() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039928-3ZF3-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserRefusedVerificationCodeChoice() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039939-SFKN-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserRefusedConfirmationMessage() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039946-TZSW-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserRefusedConfirmationMessageWithVerificationCodeChoice() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039950-XMFV-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserRefusedCertChoice() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039961-THFM-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    void signWithSmartIdUserChoosesWrongVerificationCode() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039972-5ND9-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_SELECTED_WRONG_VC);
    }

    @Disabled
    @Test
    void signWithSmartIdUserTimeout() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-30403039983-5NFT-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigningWithPollParameters(10000, 120000, flow, signatureId);
        expectSmartIdStatus(signingResponse, EXPIRED_TRANSACTION);
    }

    @Test
    void signWithSmartIdNotFound() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-49101290235-9RF6-Q"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    void headToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    void headToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    void signWithSmartIdInvalidRole() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequest("LT", null, null, null, null, null, "", null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForSidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", SID_EE_DEFAULT_DOCUMENT_NUMBER));
        addDataFile(flow, addDataFileToHashcodeRequest("file.txt", DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForSidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
