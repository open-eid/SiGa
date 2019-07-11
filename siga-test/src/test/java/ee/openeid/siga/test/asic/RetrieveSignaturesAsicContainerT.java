package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
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

public class RetrieveSignaturesAsicContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_CONTAINER_NAME));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("S0"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Test
    public void uploadHashcodeContainerWithoutSignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("container_without_signatures.bdoc"));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0]", equalTo(null));
    }

    @Test
    public void uploadHashcodeContainerWithInvalidSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknown_ocsp.asice"));

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
    public void uploadHashcodeContainerWithCorruptedInfoAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile("unknown_ocsp.asice"));

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", equalTo("S0"))
                .body("signerInfo", equalTo("SERIALNUMBER=37101010021, GIVENNAME=IGOR, SURNAME=ŽAIKOVSKI, CN=\"ŽAIKOVSKI,IGOR,37101010021\", OU=digital signature, O=ESTEID (DIGI-ID), C=EE"))
                .body("signatureProfile", equalTo("B_EPES"));
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
