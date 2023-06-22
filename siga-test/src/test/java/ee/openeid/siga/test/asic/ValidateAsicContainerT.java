package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.ERROR_CODE;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.REPORT_SIGNATURES;
import static ee.openeid.siga.test.helper.TestData.REPORT_SIGNATURES_COUNT;
import static ee.openeid.siga.test.helper.TestData.REPORT_VALID_SIGNATURES_COUNT;
import static ee.openeid.siga.test.helper.TestData.RESOURCE_NOT_FOUND;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.helper.TestData.VALIDATIONREPORT;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSigaProfileActive("datafileContainer")
class ValidateAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void validateAsicContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path("validationConclusion.policy.policyName"), equalTo("POLv4"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2014-11-17T14:11:46Z"));

        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.commonName"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.serialNumber"), equalTo("11404176865"));
    }

    @Test
    void validateDDOCContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile("ddocSingleSignature.ddoc"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));

        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.commonName"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.serialNumber"), equalTo("11404176865"));
    }

    @Test
    void validateDDOCHashcodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile("hashcodeDdoc.ddoc"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
    }

    @Test
    void validatePadesContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile("pdfSingleSignature.pdf"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
    }

    @Test
    void uploadAsicContainerAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithMultipleSignatures.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(3));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(3));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2014-11-17T14:11:46Z"));

        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.commonName"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.serialNumber"), equalTo("11404176865"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[1].subjectDistinguishedName.commonName"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[1].subjectDistinguishedName.serialNumber"), equalTo("PNOEE-38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[2].subjectDistinguishedName.commonName"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[2].subjectDistinguishedName.serialNumber"), equalTo("PNOEE-38001085718"));
    }

    @Test
    void createAsicContainerSignRemotelyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));

        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.commonName"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.serialNumber"), equalTo("PNOEE-38001085718"));
    }

    @Test
    void validateAsicContainerSignedWithExpiredOcsp() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response validationResponse = postContainerValidationReport(flow, asicContainerRequestFromFile("esteid2018signerAiaOcspLT.asice"));

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].indication"), equalTo("INDETERMINATE"));
    }

    @Test
    void validateAsicContainerWithoutSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response validationResponse = postContainerValidationReport(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(0));
    }

    @Test
    void validateAsicContainerWithEmptyDataFiles() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response validationResponse = postContainerValidationReport(flow, asicContainerRequestFromFile("signedContainerWithEmptyDatafiles.asice"));

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        // TODO: when SiVa version is at least 3.5.0, then verify empty datafiles warnings
    }

    @Test
    void validateAsicContainerWithEmptyDataFilesAndWithoutSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response validationResponse = postContainerValidationReport(flow, asicContainerRequestFromFile("unsignedContainerWithEmptyDatafiles.asice"));

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(0));
    }

    @Test
    void uploadAsicContainerWithoutSignaturesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(0));
    }

    @Test
    void uploadAsicContainerWithEmptyDataFilesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("signedContainerWithEmptyDatafiles.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        // TODO: when SiVa version is at least 3.5.0, then verify empty datafiles warnings
    }

    @Test
    void uploadAsicContainerWithEmptyDataFilesAndWithoutSignaturesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unsignedContainerWithEmptyDatafiles.asice"));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(0));
    }

    @Test
    void createAsicContainerWithoutSignaturesAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(0));
    }

    @Test
    void getValidationReportForNotExistingContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = getValidationReportForContainerInSession(flow);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    void createAsicContainerAndValidateContainerStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response containerResponse = getContainer(flow);

        Response validationResponse = postContainerValidationReport(flow, asicContainerRequest(containerResponse.getBody().path("container"), containerResponse.getBody().path("containerName")));
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test //SIGA handles this as DELETE to containerId
    void deleteToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = delete(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    void putToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = put(getContainerEndpoint() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test //SIGA handles this as DELETE to containerId
    void getToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = get(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(400));
    }

    @Test
    void headToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = head(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(400));
    }

    @Test
    void optionsToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = options(getContainerEndpoint() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToValidateAsicContainer() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        Response response = patch(getContainerEndpoint() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void deleteToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void putToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void postToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    void optionsToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToValidateAsicContainerInSession() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + VALIDATIONREPORT, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void validatePadesContainerSubjectDistinguishedName() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postContainerValidationReport(flow, asicContainerRequestFromFile("pdfSingleTestSignature.pdf"));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.commonName"), equalTo("ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].subjectDistinguishedName.serialNumber"), equalTo("11404176865"));
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
