package ee.openeid.siga.test.hashcode.upload;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.INVALID_CONTAINER;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;

@Epic("/hashcodecontainers")
@Feature("/upload/hashcodecontainers")
class DatafileNameCheckT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void uploadContainerWithExtraFilenameInSha256hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeAdditionalFilenameInSha256.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA512 hash");
    }

    @Test
    void uploadContainerWithExtraFilenameInSha512hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeAdditionalFilenameInSha512.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA256 hash");
    }

    @Test
    void uploadContainerMissingFilenameInSha256hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingFilenameInSha256.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA256 hash");
    }

    @Test
    void uploadContainerMissingFilenameInSha512hashes() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingFilenameInSha512.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA512 hash");
    }

    @Test
    void uploadContainerDifferentSha256FilenameOrder() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDifferentSha256FilenameOrder.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerDifferentSha512FilenameOrder() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDifferentSha512FilenameOrder.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerExtraSpaceInFilenameInSha256() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeAdditionalSpaceInFilenameSha256.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA512 hash");
    }

    @Test
    void uploadContainerFilenameWithUpperCaseInSha512() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameWithUpperCaseSha512.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA512 hash");
    }

    @Test
    void uploadContainerFilenameExtensionDifferentInSha512() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameExtensionDifferentSha512.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Hashcode container is missing SHA512 hash");
    }

    @Test
    void uploadContainerDifferentDatafileSizeInSha256() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDifferentDatafileSizeInSha256.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerFilenameInCyrillic() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameInCyrillic.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerFilenameOnlyAsExtension() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameOnlyAsExtension.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerFilenamesWithSpecialChars() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameWithSpecialChars.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerAdditionalFilenameInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeAdditionalFilenameInManifest.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Manifest does not contain same file names as hashcode files");
    }

    @Test
    void uploadContainerDifferentFilenameInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeDifferentFilenameInManifest.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Manifest does not contain same file names as hashcode files");
    }

    @Test
    void uploadContainerFilenameExtensionDifferentInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFilenameExtensionDifferentInManifest.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Manifest does not contain same file names as hashcode files");
    }

    @Test
    void uploadContainerDifferentFilenameOrderInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFileOrderDifferentInManifest.asice"));
        response.then().statusCode(200);
    }

    @Test
    void uploadContainerExtraSpaceInManifest() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeAdditionalSpaceInFilenameManifest.asice"));
        expectError(response, 400, INVALID_CONTAINER, "Manifest does not contain same file names as hashcode files");
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }

}
