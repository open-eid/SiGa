package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningResponse;
import io.restassured.response.Response;
import org.junit.Before;
import org.junit.Test;

import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_PEM;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;

public class GenericHashcodeSigningFlowT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void signNewHashcodeContainerWithRemoteSigningMidSid() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response midSignRequest = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String midSignatureId = midSignRequest.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, midSignatureId);

        Response sidSignRequest = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String sidSignatureId = sidSignRequest.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, sidSignatureId);

        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(3))
                .body("validationConclusion.signaturesCount", equalTo(3));
    }

    @Test
    public void signNewHashcodeContainerWithRemoteSigningMidSidCertificateChoice() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response midSignRequest = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"));
        String midSignatureId = midSignRequest.as(CreateHashcodeContainerMobileIdSigningResponse.class).getGeneratedSignatureId();
        pollForMidSigning(flow, midSignatureId);

        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("10101010005", "EE"));
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId();
        pollForSidCertificateStatus(flow, generatedCertificateId);

        Response sidSignRequest = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", "PNOEE-10101010005-Z1B2-Q"));
        String sidSignatureId = sidSignRequest.as(CreateHashcodeContainerSmartIdSigningResponse.class).getGeneratedSignatureId();
        pollForSidSigning(flow, sidSignatureId);

        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(3))
                .body("validationConclusion.signaturesCount", equalTo(3));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
