package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HashcodeContainerSystemT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createNewHashcodeContainerAndSignWithMid() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);
        Response containerResponse = getHashcodeContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteHashcodeContainer(flow);

        response = getHashcodeContainer(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void uploadHashcodeContainerAndSignRemotly() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response containerResponse = getHashcodeContainer(flow);

        assertThat(containerResponse.statusCode(), equalTo(200));
        assertThat(containerResponse.getBody().path("container").toString().length(), notNullValue());

        deleteHashcodeContainer(flow);

        Response response = getHashcodeSignatureList(flow);

        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void createHashcodeContainerSignRemotlyAndValidate() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response validationResponse = getValidationReportForContainerInSession(flow);
        assertThat(validationResponse.statusCode(), equalTo(200));
        assertThat(validationResponse.getBody().path(REPORT_VALID_SIGNATURES_COUNT), equalTo(1));

        deleteHashcodeContainer(flow);
        Response response = getValidationReportForContainerInSession(flow);
        expectError(response, 400, RESOURCE_NOT_FOUND);
    }

    @Test
    public void getSignaturesShouldReturnListOfSignatures() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        Response response = getHashcodeSignatureList(flow);
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path("signatures[0].id"), equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"));
    }


}
