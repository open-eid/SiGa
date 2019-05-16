package ee.openeid.siga.test;

import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static ee.openeid.siga.test.TestData.SIGNER_CERT_PEM;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

public class SignatureCorrectnessT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signatureValuesAreCorrectForRemoteSigning() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest(getHashcodeContainer(flow).getBody().path("container")));

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1))
                .body("validationConclusion.signatures[0].signatureFormat", equalTo("XAdES_BASELINE_LT"))
                .body("validationConclusion.signatures[0].signatureLevel", equalTo("QESIG"))
                .body("validationConclusion.signatures[0].signedBy", equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"))
                .body("validationConclusion.signatures[0].signatureScopes[0].name", equalTo("test.txt"))
                .body("validationConclusion.signatures[0].errors[0]", nullValue())
                .body("validationConclusion.signatures[0].warnings[0]", nullValue());
    }

    @Test
    public void signatureValuesAreCorrectForMidSigning() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postHashcodeMidSigningInSession(flow, hashcodeMidSigningRequestWithDefault("60001019906", "+37200000766", "LT_TM"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = postHashcodeContainerValidationReport(flow, hashcodeContainerRequest(getHashcodeContainer(flow).getBody().path("container")));

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1))
                .body("validationConclusion.signatures[0].signatureFormat", equalTo("XAdES_BASELINE_LT_TM"))
                .body("validationConclusion.signatures[0].signatureLevel", equalTo("QESIG"))
                .body("validationConclusion.signatures[0].signedBy", equalTo("O’CONNEŽ-ŠUSLIK TESTNUMBER,MARY ÄNN,60001019906"))
                .body("validationConclusion.signatures[0].signatureScopes[0].name", equalTo("test.txt"))
                .body("validationConclusion.signatures[0].errors[0]", nullValue())
                .body("validationConclusion.signatures[0].warnings[0]", nullValue());
    }

    @Test
    public void signatureWithLtaProfileSucceeds() throws Exception {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LTA")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        Response response = putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        response.then()
                .statusCode(200)
                .body("result", equalTo("OK"));
    }
}
