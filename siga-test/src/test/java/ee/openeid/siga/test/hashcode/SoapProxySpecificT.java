package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SoapProxySpecificT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient2Service7();
    }

    @Test
    void CreateHashcodeContainerWithoutSha512hashesForSoapProxy() throws Exception {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void CreateHashcodeContainerWithoutSha256hashesForSoapProxyReturnsError() throws Exception {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, null, DEFAULT_SHA512_DATAFILE , DEFAULT_FILESIZE));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    void uploadHashcodeContainerWithoutSha512hashesForSoapProxy() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha512File.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    void createContainerForSoapProxyContainsMinimalData() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, containerBase64));
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);

        assertEquals(DEFAULT_FILENAME, hashcodesSha256.getString("hashcodes.file-entry[0].@full-path"));
        assertEquals(DEFAULT_SHA256_DATAFILE, hashcodesSha256.getString("hashcodes.file-entry[0].@hash"));
        assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha256.getString("hashcodes.file-entry[0].@size"));
        assertEquals("text/plain", manifest.getString("manifest:manifest.manifest:file-entry[" + (1) + "].@manifest:media-type"));
    }

    @Test
    void createContainerForSoapProxyDoNotContainSha512file() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();

        assertFalse(getHashcodeSha512FilePresent(containerBase64), "hashcodes-sha512.xml is present");
    }

    @Test
    void addDataFileForUploadedContainerForSoapProxyDoNotContainSha512file() throws Exception {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null, DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();

        assertFalse(getHashcodeSha512FilePresent(containerBase64), "hashcodes-sha512.xml is present");
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
