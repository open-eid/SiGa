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
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.SIGNATURES;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_PEM;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSigaProfileActive("datafileContainer")
public class RetrieveSignaturesAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadAsicContainerAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("S0"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Test
    public void uploadAsicContainerWithoutSignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0]", equalTo(null));
    }

    @Test
    public void uploadAsicContainerWithInvalidSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknownOcspResponder.asice"));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("S0"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=37101010021, GIVENNAME=IGOR, SURNAME=ŽAIKOVSKI, CN=\"ŽAIKOVSKI,IGOR,37101010021\", OU=digital signature, O=ESTEID (DIGI-ID), C=EE"))
                .body("signatures[0].signatureProfile", equalTo("B_EPES"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Test
    public void createSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getSignatureList(flow);
        response.then()
                .statusCode(200)
                .body("signatures[0].id", notNullValue())
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Test
    public void uploadAsicContainerAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", equalTo("S0"))
                .body("signerInfo", equalTo("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE"))
                .body("signatureProfile", equalTo("LT"))
                .body("ocspResponseCreationTime", equalTo("2014-11-17T14:11:46Z"))
                .body("timeStampCreationTime", equalTo("2014-11-17T14:11:46Z"))
                .body("trustedSigningTime", equalTo("2014-11-17T14:11:46Z"))
                .body("claimedSigningTime", equalTo("2014-11-17T14:11:47Z"));
    }

    @Test
    public void createLtProfileSignatureAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatureProfile", equalTo("LT"))
                .body("signingCertificate", equalTo(SIGNER_CERT_PEM))
                .body("ocspCertificate", notNullValue())
                .body("timeStampTokenCertificate", notNullValue())
                .body("ocspResponseCreationTime", notNullValue())
                .body("timeStampCreationTime", notNullValue())
                .body("trustedSigningTime", notNullValue())
                .body("claimedSigningTime", notNullValue());
    }

    @Test
    public void createLtTmProfileSignatureAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT_TM")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatureProfile", equalTo("LT_TM"))
                .body("signingCertificate", equalTo(SIGNER_CERT_PEM))
                .body("ocspCertificate", notNullValue())
                .body("ocspResponseCreationTime", notNullValue())
                .body("trustedSigningTime", notNullValue())
                .body("claimedSigningTime", notNullValue());
    }

    @Test
    public void createSignatureWithSigningInfoAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatureProfile", equalTo("LT"))
                .body("signatureProductionPlace.countryName", equalTo("Estonia"))
                .body("signatureProductionPlace.city", equalTo("Tallinn"))
                .body("signatureProductionPlace.stateOrProvince", equalTo("Harju"))
                .body("signatureProductionPlace.postalCode", equalTo("4953"))
                .body("roles[0]", equalTo("Member of board"));
    }

    @Test
    public void uploadAsicContainerWithCorruptedInfoAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknownOcspResponder.asice"));

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", equalTo("S0"))
                .body("signerInfo", equalTo("SERIALNUMBER=37101010021, GIVENNAME=IGOR, SURNAME=ŽAIKOVSKI, CN=\"ŽAIKOVSKI,IGOR,37101010021\", OU=digital signature, O=ESTEID (DIGI-ID), C=EE"))
                .body("signatureProfile", equalTo("B_EPES"));
    }

    @Test
    public void deleteToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToRetrieveAsicSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void deleteToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void putToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void postToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    public void headToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void optionsToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    public void patchToRetrieveAsicSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
