package ee.openeid.siga.test;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;

class SignatureCorrectnessT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void signatureValuesAreCorrectForRemoteSigning() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = postContainerValidationReport(flow, hashcodeContainerRequest(getContainer(flow).getBody().path("container")));

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1))
                .body("validationConclusion.signatures[0].signatureFormat", equalTo("XAdES_BASELINE_LT"))
                .body("validationConclusion.signatures[0].signatureLevel", equalTo("QESIG"))
                .body("validationConclusion.signatures[0].signedBy", equalTo("JÕEORG,JAAK-KRISTJAN,38001085718"))
                .body("validationConclusion.signatures[0].signatureScopes[0].name", equalTo(DEFAULT_FILENAME))
                .body("validationConclusion.signatures[0].errors[0]", nullValue());
//              .body("validationConclusion.signatures[0].warnings[0]", nullValue()); This should be fixed in SIVA. Caused by problems in test certificate loading to TSL.
    }

    @Test
    @EnabledIfSigaProfileActive("mobileId")
    void signatureValuesAreCorrectForMidSigning() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String signatureId = response.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, signatureId);

        response = postContainerValidationReport(flow, hashcodeContainerRequest(getContainer(flow).getBody().path("container")));

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1))
                .body("validationConclusion.signatures[0].signatureFormat", equalTo("XAdES_BASELINE_LT"))
                .body("validationConclusion.signatures[0].signatureLevel", equalTo("QESIG"))
                .body("validationConclusion.signatures[0].signedBy", equalTo("O’CONNEŽ-ŠUSLIK TESTNUMBER,MARY ÄNN,60001019906"))
                .body("validationConclusion.signatures[0].signatureScopes[0].name", equalTo(DEFAULT_FILENAME))
                .body("validationConclusion.signatures[0].errors[0]", nullValue());
//              .body("validationConclusion.signatures[0].warnings[0]", nullValue()); This should be fixed in SIVA. Caused by problems in test certificate loading to TSL.
    }

    @Test
    void signatureHashcodeContainerWithLtaProfileReturnsError() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LTA")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        response.then()
                .statusCode(400)
                .body("errorCode", equalTo(INVALID_SIGNATURE));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
