package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ValidateHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void validateHashcodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("hashcodeMultipleSignatures.asice"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(3));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(3));
        assertThat(response.getBody().path("validationConclusion.policy.policyName"), equalTo("POLv4"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2019-02-22T11:04:25Z"));
    }

    @Test
    public void validateDDOCHashcodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("hashcodeDdoc.ddoc"));


        assertThat(response.statusCode(), equalTo(200));

        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path("validationConclusion.policy.policyName"), equalTo("POLv4"));
        assertThat(response.getBody().path("validationConclusion.validationWarnings.content"), hasItem("Please add Time-Stamp to the file for long term DDOC validation. This can be done with Time-Stamping application TeRa"));
        assertThat(response.getBody().path(REPORT_SIGNATURE_FORM), equalTo("DIGIDOC_XML_1.3_hashcode"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].signatureFormat"), equalTo("DIGIDOC_XML_1.3"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2012-10-03T07:46:51Z"));
    }

    @Test
    public void validateHashcodeContainerWithLTASignatureProfile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        Response response = postContainerValidationReport(flow, hashcodeContainerRequest(HASHCODE_CONTAINER_WITH_LTA_SIGNATURE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(CLIENT_EXCEPTION));
        assertThat(response.getBody().path(ERROR_MESSAGE), equalTo("Unable to validate container! Container contains signature with unsupported signature profile: LTA"));
    }

    @Test
    public void uploadHashcodeContainerAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2019-02-22T11:04:25Z"));
    }

    @Test
    public void createHashcodeContainerSignRemotelyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test
    public void validateRegularDDOCContainer() throws Exception {
        Response validationResponse = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("container.ddoc"));
        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
        assertThat(validationResponse.getBody().path(ERROR_MESSAGE), equalTo("EMBEDDED DDOC is not supported"));
    }

    @Test
    public void validateHashcodeContainerWithoutSignatures() throws Exception {
        Response validationResponse = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void validateHashcodeContainerWithZeroFileSizeDataFiles() throws Exception {
        Response validationResponse = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("hashcodeSignedContainerWithEmptyDatafiles.asice"));

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), hasItems(
                containsString("Data file 'empty-file-2.txt' is empty"),
                containsString("Data file 'empty-file-4.txt' is empty")
        ));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-1.txt"))));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-3.txt"))));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-5.txt"))));
    }

    @Test
    public void validateHashcodeContainerWithZeroFileSizeDataFilesAndWithoutSignatures() throws Exception {
        Response validationResponse = postContainerValidationReport(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
        assertThat(validationResponse.getBody().path(ERROR_MESSAGE), equalTo("Missing signatures"));
    }

    @Test
    public void uploadHashcodeContainerWithoutSignaturesAndValidateInSession() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
    }

    @Test
    public void uploadHashcodeContainerWithZeroFileSizeDataFilesAndValidateInSession() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeSignedContainerWithEmptyDatafiles.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), hasItems(
                containsString("Data file 'empty-file-2.txt' is empty"),
                containsString("Data file 'empty-file-4.txt' is empty")
        ));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-1.txt"))));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-3.txt"))));
        assertThat(validationResponse.getBody().path("validationConclusion.signatures[0].warnings.content"), everyItem(not(containsString("data-file-5.txt"))));
    }

    @Test
    public void uploadHashcodeContainerWithZeroFileSizeDataFilesAndWithoutSignaturesAndValidateInSession() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(400));
        assertThat(validationResponse.getBody().path(ERROR_CODE), equalTo(INVALID_CONTAINER_EXCEPTION));
        assertThat(validationResponse.getBody().path(ERROR_MESSAGE), equalTo("Missing signatures"));
    }

    @Test
    public void createHashcodeContainerWithoutSignaturesAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

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
    public void createHashcodeContainerAndValidateContainerStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response containerResponse = getContainer(flow);

        Response validationResponse = postContainerValidationReport(flow, hashcodeContainerRequest(containerResponse.getBody().path("container")));
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test //SIGA handles this as DELETE to containerId
    public void deleteToValidateHashcodeContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = delete(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void headToValidateHashcodeContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
