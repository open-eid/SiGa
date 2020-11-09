package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
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

public class RetrieveSignaturesHashcodeContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Ignore("This test jams the system")
    @Test
    public void uploadHashcodeContainerWithManySignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("asice-1000-signatures.asice"));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[999].id", equalTo("id-"))
                .body("signatures[999].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[999].signatureProfile", equalTo("LT"))
                .body("signatures[999].generatedSignatureId", notNullValue());
    }

    @Test
    public void uploadHashcodeContainerWithoutSignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = getSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0]", equalTo(null));
    }

    @Test
    public void createSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
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
    public void uploadHashcodeContainerAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"))
                .body("signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatureProfile", equalTo("LT"))
                .body("ocspResponseCreationTime", equalTo("2019-02-22T11:04:27Z"))
                .body("timeStampCreationTime", equalTo("2019-02-22T11:04:25Z"))
                .body("trustedSigningTime", equalTo("2019-02-22T11:04:25Z"))
                .body("claimedSigningTime", equalTo("2019-02-22T11:04:24Z"));
    }

    @Test
    public void createLtProfileSignatureAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
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
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT_TM")).as(CreateHashcodeContainerRemoteSigningResponse.class);
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
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia")).as(CreateHashcodeContainerRemoteSigningResponse.class);
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
    public void uploadHashcodeContainerWithInvalidSignature() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidOcspValue.asice"));
        
        Response response = getSignatureList(flow);
        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(INVALID_SIGNATURE_EXCEPTION));
    }

    @Test
    public void uploadHashcodeContainerWithHashMismatchAndRetrieveSignatureInfo() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeSha512HashMismatch.asice"));

        Response response = getSignatureInfo(flow, getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"));

        response.then()
                .statusCode(200)
                .body("id", equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"))
                .body("signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatureProfile", equalTo("LT"))
                .body("signingCertificate", notNullValue())
                .body("ocspCertificate", notNullValue())
                .body("ocspResponseCreationTime", notNullValue())
                .body("trustedSigningTime", notNullValue())
                .body("claimedSigningTime", notNullValue());
    }

    @Test
    public void uploadHashcodeAndRetrieveSignatureInfoWithWrongId() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));

        Response response = getSignatureInfo(flow, "randomSignatureId");

        response.then()
                .statusCode(400)
                .body(ERROR_CODE, equalTo(RESOURCE_NOT_FOUND));
    }

    @Test
    public void headToRetrieveHashcodeSignatureList() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES, flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Test
    public void headToRetrieveHashcodeSignatureInfo() throws NoSuchAlgorithmException, InvalidKeyException, JSONException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + SIGNATURES + "/" + getSignatureList(flow).getBody().path("signatures[0].generatedSignatureId"), flow);

        assertThat(response.statusCode(), equalTo(200));
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
