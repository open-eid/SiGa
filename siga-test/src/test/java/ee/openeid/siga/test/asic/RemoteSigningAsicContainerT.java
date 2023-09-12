package ee.openeid.siga.test.asic;

import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import io.restassured.response.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.AUTH_CERT_PEM;
import static ee.openeid.siga.test.helper.TestData.AUTH_CERT_PEM_HEX;
import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.DATA_TO_SIGN;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DIGEST_ALGO;
import static ee.openeid.siga.test.helper.TestData.INVALID_CERTIFICATE_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.INVALID_REQUEST;
import static ee.openeid.siga.test.helper.TestData.INVALID_SESSION_DATA_EXCEPTION;
import static ee.openeid.siga.test.helper.TestData.INVALID_SIGNATURE;
import static ee.openeid.siga.test.helper.TestData.MID_SID_CERT_REMOTE_SIGNING;
import static ee.openeid.siga.test.helper.TestData.REMOTE_SIGNING;
import static ee.openeid.siga.test.helper.TestData.RESULT;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_EXPIRED_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_EXPIRED_PEM_HEX;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_MID_PEM;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_PEM_HEX;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.addDataFileToAsicRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@EnabledIfSigaProfileActive("datafileContainer")
class RemoteSigningAsicContainerT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void signNewAsicContainerRemotely() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void signNewAsicContainerMultipleSignaturesPerContainerSuccessfully() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        CreateContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse2.getDataToSign(), dataToSignResponse2.getDigestAlgorithm())), dataToSignResponse2.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    void signNewAsicContainerValidAndInvalidSignatureValue() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        CreateContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse1.getDataToSign(), dataToSignResponse1.getDigestAlgorithm())), dataToSignResponse1.getGeneratedSignatureId());
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse2.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void signNewAsicContainerRemotelyWithCertInHex() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(1))
                .body("validationConclusion.signaturesCount", equalTo(1));
    }

    @Test
    void addSignatureToAsicContainerRemotely() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        Response response = getValidationReportForContainerInSession(flow);

        response.then()
                .statusCode(200)
                .body("validationConclusion.validSignaturesCount", equalTo(2))
                .body("validationConclusion.signaturesCount", equalTo(2));
    }

    @Test
    void signAsicContainerRemotelyWithMultipleSignatures() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse1 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        CreateContainerRemoteSigningResponse dataToSignResponse2 = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT")).as(CreateContainerRemoteSigningResponse.class);

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
    void startAsicRemoteSigningContainerReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startAsicRemoteSigningContainerWithHexEncodedCertificateReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM_HEX, "LT"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startAsicRemoteSigningContainerWithAllParamsReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "Member of board", "Tallinn", "Harju", "4953", "Estonia"));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startAsicRemoteSigningContainerWithRoleReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "Member of board", null, null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startAsicRemoteSigningContainerWithLocationReturnsDigestToSign() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", null, "Tallinn", null, null, null));

        response.then()
                .statusCode(200)
                .body(DATA_TO_SIGN, notNullValue())
                .body(DIGEST_ALGO, equalTo("SHA512"));
    }

    @Test
    void startAsicRemoteSigningContainerEmptyBody() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        Response response = postRemoteSigningInSession(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerMissingSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        Response response = postRemoteSigningInSession(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerMissingProfile() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        JSONObject request = new JSONObject();
        request.put("signingCertificate", SIGNER_CERT_ESTEID2018_PEM);
        Response response = postRemoteSigningInSession(flow, request);

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerEmptySigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerInvalidSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("-&32%", "LT"));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM, "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerHexEncodedAuthenticationCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(AUTH_CERT_PEM_HEX, "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM, "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerHexEncodedExpiredSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_EXPIRED_PEM_HEX, "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerInvalidBase64EncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("Y2VydA==", "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @Test
    void startAsicRemoteSigningContainerInvalidHexEncodedSigningCertificate() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault("435254", "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION);
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Remotely signing new ASIC container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void signNewAsicContainerRemotelyWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @DisplayName("Signing not allowed with invalid signature profiles")
    @ParameterizedTest(name = "Remotely signing uploaded ASIC container not allowed with signatureProfile = ''{0}''")
    @MethodSource("provideInvalidSignatureProfiles")
    void signUploadAsicRemoteSigningContainerWithInvalidSignatureProfile(String signatureProfile) throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, signatureProfile));

        expectError(response, 400, INVALID_REQUEST, "Invalid signature profile");
    }

    @Test
    void startAsicRemoteSigningContainerInvalidRole() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequest(SIGNER_CERT_ESTEID2018_PEM, "LT", "", null, null, null, null));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void startAsicRemoteSigningContainerMidCertificate() throws Exception {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_MID_PEM, "LT"));

        expectError(response, 400, INVALID_CERTIFICATE_EXCEPTION, MID_SID_CERT_REMOTE_SIGNING);
    }

    @Test
    void startAsicRemoteSigningContainerEmptyDataFiles() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("unsignedContainerWithEmptyDatafiles.asice"));

        Response response = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT"));

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION, "Unable to sign container with empty datafiles");
    }

    @Test
    void finalizeRemoteSigningContainerReturnsOk() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.getBody().path(RESULT), equalTo(Result.OK.name()));
    }

    @Test
    void finalizeRemoteSigningContainerWithEmptyBody() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        JSONObject request = new JSONObject();
        Response response = putRemoteSigningInSession(flow, request, dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void finalizeRemoteSigningContainerWithEmptySignatureValue() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(""), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void finalizeRemoteSigningContainerWithInvalidSignatureValue() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest("yW9mTV2U+Hfl5EArvg9evTgb0BSHp/p9brr1K5bBIsE="), dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SIGNATURE);
    }

    @Test
    void containerDataFilesChangedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow,
                remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        deleteDataFile(flow, getDataFileList(flow).getBody().path("dataFiles[0].fileName"));

        Response response = putRemoteSigningInSession(flow,
                remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())),
                dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void containerDataFilesAddedBeforeFinalizeReturnsError() throws Exception {
        postUploadContainer(flow, asicContainerRequestFromFile("containerWithoutSignatures.asice"));
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow,
                remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        addDataFile(flow, addDataFileToAsicRequest("testFile.txt", "eWV0IGFub3RoZXIgdGVzdCBmaWxlIGNvbnRlbnQu"));

        Response response = putRemoteSigningInSession(flow,
                remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())),
                dataToSignResponse.getGeneratedSignatureId());

        expectError(response, 400, INVALID_SESSION_DATA_EXCEPTION);
    }

    @Test
    void deleteToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void putToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = put(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void getToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void optionsToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToStartAsicRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING, flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void deleteToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = delete(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void getToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = get(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void postToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = post(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow, "request");

        expectError(response, 405, INVALID_REQUEST);
    }

    @Test
    void headToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = head(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void optionsToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = options(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow);

        assertThat(response.statusCode(), equalTo(405));
    }

    @Test
    void patchToAsicFinalizeRemoteSigning() throws NoSuchAlgorithmException, InvalidKeyException, JSONException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse startResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);

        Response response = patch(getContainerEndpoint() + "/" + flow.getContainerId() + REMOTE_SIGNING + "/" + startResponse.getGeneratedSignatureId(), flow);

        expectError(response, 405, INVALID_REQUEST);
    }

    @Override
    public String getContainerEndpoint() {
        return CONTAINERS;
    }
}
