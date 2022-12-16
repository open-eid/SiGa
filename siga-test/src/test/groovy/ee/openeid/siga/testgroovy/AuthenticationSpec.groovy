package ee.openeid.siga.testgroovy

import ee.openeid.siga.test.model.SigaApiFlow
import ee.openeid.siga.testgroovy.helper.TestBaseSpecification
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceResponse
import ee.openeid.siga.webapp.json.GetContainerSmartIdCertificateChoiceStatusResponse
import io.restassured.response.Response
import org.junit.Test

import static ee.openeid.siga.test.helper.TestData.CONTAINERS
import static ee.openeid.siga.test.utils.RequestBuilder.*
import static org.hamcrest.CoreMatchers.equalTo

class AuthenticationSpec extends TestBaseSpecification {

    private SigaApiFlow flow

    def setup() {
        flow = SigaApiFlow.buildForTestClient3Service1()
    }

    @Test
    def "Sign with SID as Client 3 (no contact info)"() {
        expect:
        postCreateContainer(flow, asicContainersDataRequestWithDefault())
        Response certificateChoice = postSidCertificateChoice(flow, smartIdCertificateChoiceRequest("30303039914", "EE"))
        String generatedCertificateId = certificateChoice.as(CreateHashcodeContainerSmartIdCertificateChoiceResponse.class).getGeneratedCertificateId()

        pollForSidCertificateStatus(flow, generatedCertificateId)

        String documentNumber = flow.getSidCertificateStatus().as(GetContainerSmartIdCertificateChoiceStatusResponse.class).getDocumentNumber()
        Response signingResponse = postSmartIdSigningInSession(flow, smartIdSigningRequestWithDefault("LT", documentNumber))
        String generatedSignatureId = signingResponse.as(CreateContainerSmartIdSigningResponse.class).getGeneratedSignatureId()
        pollForSidSigning(flow, generatedSignatureId)

        Response validationResponse = getValidationReportForContainerInSession(flow)

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
    }

    @Test
    def "Sign with MID as Client 3 (no contact info)"() {
        expect:
        postCreateContainer(flow, asicContainersDataRequestWithDefault())
        Response response = postMidSigningInSession(flow, midSigningRequestWithDefault("60001019906", "+37200000766", "LT"))
        String signatureId = response.as(CreateContainerMobileIdSigningResponse.class).getGeneratedSignatureId()
        pollForMidSigning(flow, signatureId)

        Response validationResponse = getValidationReportForContainerInSession(flow)

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
    }

    @Override
    String getContainerEndpoint() {
        CONTAINERS
    }
}
