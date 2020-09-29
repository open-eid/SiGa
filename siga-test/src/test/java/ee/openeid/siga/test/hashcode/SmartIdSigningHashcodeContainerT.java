package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.*;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class SmartIdSigningHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signWithSmartIdWithCertificateChoiceSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);
        String documentNumber = certificateStatus.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();

        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void smartIdSigningStatusRequestAfterSuccessfulFinalization() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);
        String documentNumber = certificateStatus.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();

        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String signatureId = signingResponse.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response signAfterFinalizationResponse = getSmartIdSigningInSession(flow, signatureId);

        expectError(signAfterFinalizationResponse, 400, INVALID_DATA);
    }

    @Test
    public void postWithSmartIdCertificateChoiceSymbolsInPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest(".!:", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void postWithSmartIdCertificateChoiceInvalidFormatPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("39101290235", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void postWithSmartIdCertificateChoiceMissingPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("country", "EE");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void postWithSmartIdCertificateChoiceMissingCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "10101010005");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void postWithSmartIdCertificateChoicePersonIdentifierCountryMismatch() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        JSONObject request = new JSONObject();
        request.put("personIdentifier", "10101010016");
        request.put("country", "LT");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, request.toString());

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void postWithSmartIdCertificateChoiceEmptyPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("", "EE"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void postWithSmartIdCertificateChoiceInvalidCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "ee"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void getSmartIdCertificateChoiceInvalidCertificateId() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        Response response = getSidCertificateStatus(flow, "00000000-0000-0000-0000-000000000000");

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void signSmartIdCertificateChoiceInvalidFormatDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010006-Z1B2-Q"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void signSmartIdCertificateChoiceEmptyDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", ""));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void signSmartIdCertificateChoiceMissingDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, request.toString());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void getSmartIdSidStatusCertificateReturned() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);
        String sidStatus = certificateStatus.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getSidStatus();

        assertThat(sidStatus, is("CERTIFICATE"));
    }

    @Test
    public void getSmartIdSidStatusErrorOnSecondRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        getSidCertificateStatus(flow, generatedCertificateId);
        Response certificateStatus = getSidCertificateStatus(flow, generatedCertificateId);

        expectError(certificateStatus, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Ignore
    @Test
    public void smartIdCertificateChoiceUnauthorizedCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("010101-10006", "LV"));

        expectError(certificateChoice, 400, INVALID_REQUEST);
    }

    @Ignore
    @Test
    public void smartIdCertificateChoiceAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101020001", "LT"));

        expectError(certificateChoice, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void signWithSmartIdSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, signatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Ignore
    @Test
    public void signWithSmartIdUnauthorizedCountry() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOLV-010101-10006-SGT7-Q"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Ignore
    @Test
    public void signWithSmartIdAdvancedCertificateLevel() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOLT-10101020001-K87V-NQ"));

        expectError(response, 400, CLIENT_EXCEPTION);
    }

    @Test
    public void signWithSmartIdUserRefused() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010016-9RF6-Q"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response signingResponse = pollForSidSigning(flow, signatureId);
        expectSmartIdStatus(signingResponse, USER_CANCEL);
    }

    @Test
    public void signWithSmartIdNotFound() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-123abc-9RF6-Q"));
        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void deleteToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartHashcodeSmartIdSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void getToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void optionsToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToStartCertificateChoice() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToHashcodeSmartIdSigningStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response startResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        String signatureId = startResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + "/" + signatureId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToHashcodeSmartIdSCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response certificateChoice =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void signWithSmartIdInvalidRole() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequest("EE", "LT", null, null, null, null, null, "",null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        String signatureId = response.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        Response pollResponse = pollForSidSigning(flow, signatureId);

        expectError(pollResponse, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT","PNOEE-10101010005-Z1B2-Q"));
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
