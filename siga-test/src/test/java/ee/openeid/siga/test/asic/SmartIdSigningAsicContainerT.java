package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.AssumingProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.GetContainerSmartIdCertificateChoiceStatusResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SmartIdSigningAsicContainerT extends TestBase {

    @ClassRule
    public static AssumingProfileActive assumingRule = new AssumingProfileActive("datafileContainer");

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }
    
    @Test
    public void signWithSmartIdWithCertificateChoiceSuccessfullyEstonia() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Ignore
    @Test
    public void signWithSmartIdWithCertificateChoiceSuccessfullyLatvia() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("010101-10006", "LV"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Ignore
    @Test
    public void signWithSmartIdWithCertificateChoiceSuccessfullyLithuania() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "LT"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void smartIdSigningStatusRequestAfterSuccessfulFinalization() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        pollForSidCertificateStatus(flow, generatedCertificateId);

        String documentNumber = flow.getSidCertificateStatus().as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response signAfterFinalizationResponse = getSmartIdSigningInSession(flow, generatedSignatureId);

        expectError(signAfterFinalizationResponse, 400, INVALID_DATA);
    }

    @Test
    public void postWithSmartIdCertificateChoiceSymbolsInPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest(".!:", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void postWithSmartIdCertificateChoiceInvalidFormatPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("39101290235", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void postWithSmartIdCertificateChoiceMissingPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("country", "EE");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void postWithSmartIdCertificateChoiceMissingCountry() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "10101010005");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Ignore
    @Test
    public void postWithSmartIdCertificateChoicePersonIdentifierCountryMismatch() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "10101010016");
        request.put("country", "LV");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, SMARTID_EXCEPTION);
    }

    @Test
    public void postWithSmartIdCertificateChoiceEmptyPersonIdentifier() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("", "EE"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void postWithSmartIdCertificateChoiceInvalidCountry() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "ee"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void getSmartIdCertificateChoiceInvalidCertificateId() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        Response response = pollForSidCertificateStatus(flow, "00000000-0000-0000-0000-000000000000");

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void signSmartIdInvalidFormatDocumentNumber() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE.10101010005-Z1B2-Q"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void signSmartIdEmptyDocumentNumber() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void signSmartIdMissingDocumentNumber() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void getSmartIdSidStatusCertificateReturned() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response certificateStatus = pollForSidCertificateStatus(flow, generatedCertificateId);
        String sidCertificateStatus = certificateStatus.as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getSidStatus();

        assertThat(sidCertificateStatus, is("CERTIFICATE"));
    }

    @Test
    public void getSmartIdSidStatusErrorOnSecondRequest() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId);
        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);

        expectError(certificateStatus, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Ignore
    @Test
    public void smartIdCertificateChoiceAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101020001", "LT"));

        expectError(certificateChoice, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void signWithSmartIdSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = response.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Ignore
    @Test
    public void signWithSmartIdAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOLT-10101020001-K87V-NQ"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void signWithSmartIdUserRefused() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010016-9RF6-Q"));
        String signatureId = response.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    public void signWithSmartIdNotFound() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-123abc-9RF6-Q"));
        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void signWithSmartIdNonExistingSignatureProfile() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("QES", null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void signWithSmartIdInvalidRole() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequest("EE", "LT", null, null, null, null, null, "", null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        String signatureId = response.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForSidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));
        String signatureId = response.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForSidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void deleteToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartAsicSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }
    
    @Test
    public void deleteToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartAsicCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }
    
    @Test
    public void deleteToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToAsicSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }
    
    @Test
    public void deleteToAsicSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToAsicSmartIdSCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToAsicSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToAsicSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToAsicSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToAsicSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
