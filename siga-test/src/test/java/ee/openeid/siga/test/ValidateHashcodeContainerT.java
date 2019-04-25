package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class ValidateHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void validateHascodeContainer() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(response.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2019-02-22T11:04:25Z"));
    }

    @Test
    public void validateHascodeContainerWithLTASignatureProfile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        Response response = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest(HASHCODE_CONTAINER_WITH_LTA_SIGNATURE));
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo("CLIENT_EXCEPTION"));
        assertThat(response.getBody().path(ERROR_MESSAGE), equalTo("Unable to validate container! Container contains signature with unsupported signature profile: LTA"));
    }

    @Test
    public void uploadHashcodeContainerAndValidateInSession() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES_COUNT), equalTo(1));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].signedBy"), equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"));
        assertThat(validationResponse.getBody().path(REPORT_SIGNATURES + "[0].info.bestSignatureTime"), equalTo("2019-02-22T11:04:25Z"));
    }

    @Test
    public void createHashcodeContainerSignRemotlyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

    @Test
    public void getValidationReportForNotExistingContainer() throws NoSuchAlgorithmException, InvalidKeyException {
        Response response = getValidationReportForContainerInSession(flow);
        assertThat(response.statusCode(), equalTo(400));
        assertThat(response.getBody().path(ERROR_CODE), equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void createHashcodeContainerAndValidateContainerStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response containerResponse = getHashcodeContainer(flow);

        Response validationResponse = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest(containerResponse.getBody().path("container")));
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));
    }

}
