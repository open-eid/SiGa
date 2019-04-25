package ee.openeid.siga.test;

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

import static ee.openeid.siga.test.TestData.DEFAULT_HASHCODE_CONTAINER;
import static ee.openeid.siga.test.TestData.SIGNER_CERT_PEM;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

public class RetrieveSignaturesHashcodeContainerT extends TestBase {
    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void uploadHashcodeContainerAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Ignore
    @Test
    public void uploadHashcodeContainerWithManySignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("asice-1000-signatures.asice"));

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[999].id", equalTo("id-"))
                .body("signatures[999].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[999].signatureProfile", equalTo("LT"))
                .body("signatures[999].generatedSignatureId", notNullValue());
    }

    @Test
    public void uploadHashcodeContainerWithoutSignaturesAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0]", equalTo(null));
    }

    @Test
    public void uploadHashcodeContainerWithInvalidSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadHashcodeContainer(flow, hashcodeContainerRequestFromFile("hashcodeInvalidOcspValue.asice"));

        Response response = getHashcodeSignatureList(flow);

        response.then()
                .statusCode(200)
                .body("signatures[0].id", equalTo("id-a9fae00496ae203a6a8b92adbe762bd3"))
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }

    @Test
    public void createSignatureAndRetrieveSignatureList() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateHashcodeContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putHashcodeRemoteSigningInSession(flow, hashcodeRemoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getHashcodeSignatureList(flow);
        response.then()
                .statusCode(200)
                .body("signatures[0].id", notNullValue())
                .body("signatures[0].signerInfo", equalTo("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE"))
                .body("signatures[0].signatureProfile", equalTo("LT"))
                .body("signatures[0].generatedSignatureId", notNullValue());
    }
}
