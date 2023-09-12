package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static ee.openeid.siga.test.helper.TestData.AUTH_CERT_PEM;
import static ee.openeid.siga.test.helper.TestData.AUTH_CERT_PEM_HEX;
import static ee.openeid.siga.test.helper.TestData.DATA_TO_SIGN;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_HASHCODE_CONTAINER;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DIGEST_ALGO;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_CERTIFICATE_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.INVALID_SESSION_DATA_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.INVALID_SIGNATURE;
import static ee.openeid.siga.test.helper.TestData.MID_SID_CERT_REMOTE_SIGNING;
import static ee.openeid.siga.test.helper.TestData.RESULT;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_EXPIRED_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_EXPIRED_PEM_HEX;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_MID_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_PEM_HEX;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToHashcodeRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class RemoteSigningHashcodeContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void addSignatureToHashcodeContainerRemotely() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    void signNewHashcodeContainerRemotely() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void signNewHashcodeContainerMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse2.getDataToSign(), dataToSignResponse2.getDigestAlgorithm())), dataToSignResponse2.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    void signNewHashcodeContainerValidAndInvalidSignatureValue() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse2.getGeneratedSignatureId());
        Response response = getValidationReportForContainerInSession(flow);
        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void signNewHashcodeContainerRemotelyWithCertInHex() throws Exception {
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
    void signContainerRemotelyWithMultipleSignatures() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
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
    void startRemoteSigningHashcodeContainerReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startRemoteSigningHashcodeContainerWithHexEncodedCertificateReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startRemoteSigningHashcodeContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startRemoteSigningHashcodeContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "Member of board", null, null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startRemoteSigningHashcodeContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startRemoteSigningHashcodeContainerEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startRemoteSigningHashcodeContainerMissingSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startRemoteSigningHashcodeContainerMissingProfile() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_ESTEID2018_PEM);
        Response response = postRemoteSigningInSession(flow, request);
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startRemoteSigningHashcodeContainerEmptySigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Remotely signing new hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void signNewHashcodeContainerRemotelyWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Remotely signing uploaded hashcode container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void uploadHashcodeRemoteSigningContainerWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @Test
    void startRemoteSigningHashcodeContainerInvalidSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("-&32%", "LT"));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startRemoteSigningHashcodeContainerAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startRemoteSigningHashcodeContainerHexEncodedAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM_HEX, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startRemoteSigningHashcodeContainerExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startRemoteSigningHashcodeContainerHexEncodedExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM_HEX, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startRemoteSigningHashcodeContainerInvalidBase64EncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("Y2VydA==", "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startRemoteSigningHashcodeContainerInvalidHexEncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("435254", "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerMidCertificate() throws Exception {
        postCreateContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_MID_PEM, "LT"));
        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION, MID_SID_CERT_REMOTE_SIGNING);
    }

    @Test
    void startRemoteSigningHashcodeContainerInvalidRole() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "", null, null, null, null));
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startRemoteSigningHashcodeContainerEmptyDataFiles() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeUnsignedContainerWithEmptyDatafiles.asice"));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));
        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    void finalizeRemoteSigningHashcodeContainerReturnsOk() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    void finalizeRemoteSigningHashcodeContainerWithEmptyBody() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        JSONObject request = new JSONObject();
        Response response = putRemoteSigningInSession(flow, request, dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void finalizeRemoteSigningHashcodeContainerWithEmptySignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(""), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void finalizeRemoteSigningHashcodeContainerWithInvalidSignatureValue() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequest(DEFAULT_HASHCODE_CONTAINER));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse.getGeneratedSignatureId());
        expectError(response, 400, INVALID_SIGNATURE);
    }

    @Test
    void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));
        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE));
        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
