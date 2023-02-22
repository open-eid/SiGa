package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeContainerSystemT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createNewHashcodeContainerAndSignWithMid() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        Response containerResponse = getContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteContainer(flow);

        response = getContainer(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void uploadHashcodeContainerAndSignRemotely() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response containerResponse = getContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteContainer(flow);

        Response response = getSignatureList(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void createHashcodeContainerSignRemotelyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));

        deleteContainer(flow);
        Response response = getValidationReportForContainerInSession(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void getSignaturesShouldReturnListOfSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = getSignatureList(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("signatures[0].id"), equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"));
    }


    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
