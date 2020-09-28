package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.*;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
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
        Response response1 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = getSidCertificateStatus(flow, generatedCertificateId);
        String documentNumber = response2.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();

        Response response3 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = response3.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1));
    }

    @Test
    public void invalidSmartIdSigningStatusRequestAfterSuccessfulFinalization() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = getSidCertificateStatus(flow, generatedCertificateId);
        String documentNumber = response2.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber();

        Response response3 = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber));
        String generatedSignatureId = response3.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, generatedSignatureId);

        Response response4 = getSmartIdSigningInSession(flow, generatedSignatureId);

        expectError(response4, 400, INVALID_DATA);
    }

    @Test
    public void postWithSmartIdCertificateChoiceInvalidPersonIdentifier() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("P!NO-23a.31,23", "EE"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void postWithSmartIdCertificateChoiceMissingPersonIdentifierFormat() throws Exception {
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
    public void signSmartIdCertificateChoiceInvalidDocumentNumber() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        Response response = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010006-Z1B2-Q"));

        expectError(response, 400, SMARTID_EXCEPTION, NOT_FOUND);
    }

    @Test
    public void getSmartIdSidStatusCertificateReturned() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        Response response2 = getSidCertificateStatus(flow, generatedCertificateId);
        String sidCertificateStatus1 = response2.as(GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class).getSidStatus();

        assertThat(sidCertificateStatus1, is("CERTIFICATE"));
    }

    @Test
    public void getSmartIdSidStatusErrorOnSecondRequest() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        getSidCertificateStatus(flow, generatedCertificateId);
        Response response2 = getSidCertificateStatus(flow, generatedCertificateId);

        expectError(response2, 400, INVALID_SESSION_DATA_EXCEPTION);
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
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response2, 405, INVALID_REQUEST);
    }

    @Test
    public void putToHashcodeSmartIdSCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = put(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response2, 405, INVALID_REQUEST);
    }

    @Test
    public void postToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = post(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow, "request");

        expectError(response2, 405, INVALID_REQUEST);
    }

    @Test
    public void headToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = head(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        assertThat(response2.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = options(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response2, 405, INVALID_REQUEST);
    }

    @Test
    public void patchToHashcodeSmartIdCertificateStatus() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response1 =  postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = response1.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();

        Response response2 = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SMARTID_SIGNING + CERTIFICATE_CHOICE + "/" + generatedCertificateId + STATUS, flow);

        expectError(response2, 405, INVALID_REQUEST);
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
