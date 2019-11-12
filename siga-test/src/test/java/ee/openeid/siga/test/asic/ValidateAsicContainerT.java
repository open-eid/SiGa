package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.AssumingProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class ValidateAsicContainerT extends TestBase {

    @ClassRule
    public static AssumingProfileActive assumingRule = new AssumingProfileActive("datafileContainer");

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void validateAsicContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path("validationConclusion.policy.policyName"), equalTo("POLv4"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2014-11-17T14:11:46Z"));
    }

    @Test
    public void uploadAsicContainerAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithMultipleSignatures.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(3));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(3));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2014-11-17T14:11:46Z"));
    }

    @Test
    public void createAsicContainerSignRemotelyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test
    public void validateAsicContainerWithoutSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response validationResponse = postContainerValidationReport(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void uploadAsicContainerWithoutSignaturesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void createAsicContainerWithoutSignaturesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void getValidationReportForNotExistingContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = getValidationReportForContainerInSession(flow);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void createAsicContainerAndValidateContainerStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response containerResponse = getContainer(flow);

        Response validationResponse = postContainerValidationReport(flow, asicContainerRequest(containerResponse.getBody().path("container"), containerResponse.getBody().path("containerName")));
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test //SIGA handles this as DELETE to containerId
    public void deleteToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = delete(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void putToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = put(getContainerEndpoint() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test //SIGA handles this as DELETE to containerId
    public void getToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = get(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(400));
    }

    @Test
    public void headToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = head(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(400));
    }

    @Test
    public void optionsToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = options(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = patch(getContainerEndpoint() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
