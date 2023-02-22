package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RemoteSigningHachcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void addSignatureToHashcodeContainerRemotely() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    public void signNewHashcodeContainerRemotely() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void signNewHashcodeContainerMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse2.getDataToSign(), dataToSignResponse2.getDigestAlgorithm())), dataToSignResponse2.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    public void signNewHashcodeContainerValidAndInvalidSignatureValue() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse2.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void signNewHashcodeContainerRemotelyWithCertInHex() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        getContainer(flow);
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    public void signContainerRemotelyWithMultipleSignatures() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());

        response.then().statusCode(200).body("result", equalTo(Result.OK.name()));

        response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse2.getDataToSign(), dataToSignResponse2.getDigestAlgorithm())), dataToSignResponse2.getGeneratedSignatureId());

        response.then().statusCode(200).body("result", equalTo(Result.OK.name()));

        Response validationResponse = getValidationReportForContainerInSession(flow);

        validationResponse.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(3))
                .body("validationConclusion.signaturesCount", equalTo(3));
    }

    @Test
    public void startRemoteSigningHashcodeContainerReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithHexEncodedCertificateReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "Member of board", null, null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerMissingProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_PEM);
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptySigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, ""));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("-&32%", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startRemoteSigningHashcodeContainerHexEncodedAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM_HEX, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startRemoteSigningHashcodeContainerExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startRemoteSigningHashcodeContainerHexEncodedExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM_HEX, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidBase64EncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("Y2VydA==", "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidHexEncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("435254", "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    public void startAsicRemoteSigningContainerMidCertificate() throws Exception {
        postCreateContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_MID_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION, MID_SID_CERT_REMOTE_SIGNING);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfileFormat() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "123"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "B_BES"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerInvalidRole() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_PEM, "LT", "", null, null, null, null));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void startRemoteSigningHashcodeContainerEmptyDataFiles() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT"));
        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerReturnsOk() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        JSONObject request = new JSONObject();
        Response response = putRemoteSigningInSession(flow, request, dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithEmptySignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(""), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void finalizeRemoteSigningHashcodeContainerWithInvalidSignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_SIGNATURE);
    }

    @Test
    public void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    public void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
