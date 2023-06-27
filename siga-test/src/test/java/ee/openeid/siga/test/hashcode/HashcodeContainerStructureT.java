package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.path.xml.XmlPath;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static ee.openeid.siga.test.helper.TestData.CONTAINER;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_HASHCODE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA256_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_SHA512_DATAFILE;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_SHA256;
import static ee.openeid.siga.test.helper.TestData.HASHCODE_SHA512;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.MIMETYPE;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.hashcodeDataFileAsXmlPath;
import static ee.openeid.siga.test.utils.ContainerUtil.manifestAsXmlPath;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequest;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestDataFile;
import static ee.openeid.siga.test.utils.RequestBuilder.hashcodeContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("/hashcodecontainers")
@Feature("Verify hashcode container structure")
class HashcodeContainerStructureT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void createHashcodeContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        CreateHashcodeContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateHashcodeContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA256, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA512, containerBase64));
        assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    void uploadHashcodeContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA256, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA512, containerBase64));
        assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    void createHashcodeContainerAndVerifyHashcode256File() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);

        assertEquals(DEFAULT_FILENAME, hashcodesSha256.getString("hashcodes.file-entry[0].@full-path"));
        assertEquals(DEFAULT_SHA256_DATAFILE, hashcodesSha256.getString("hashcodes.file-entry[0].@hash"));
        assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha256.getString("hashcodes.file-entry[0].@size"));
    }

    @Test
    void createHashcodeContainerAndVerifyHashcode512File() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        XmlPath hashcodesSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertEquals(DEFAULT_FILENAME, hashcodesSha512.getString("hashcodes.file-entry[0].@full-path"));
        assertEquals(DEFAULT_SHA512_DATAFILE, hashcodesSha512.getString("hashcodes.file-entry[0].@hash"));
        assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha512.getString("hashcodes.file-entry[0].@size"));
    }

    @Test
    void createHashcodeContainerAndVerifyMimetypeFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        String mimeType = new String(extractEntryFromContainer(MIMETYPE, containerBase64));

        assertEquals("application/vnd.etsi.asic-e+zip", mimeType);
    }

    @ParameterizedTest
    @CsvSource({"testFile1.xml, testFile2.xml", "testFile2.xml, testFile1.xml"})
    void createHashcodeContainerAndVerifyFileOrder(String fileName1, String fileName2) throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject dataFile1 = hashcodeContainersDataRequestDataFile(fileName1, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
        JSONObject dataFile2 = hashcodeContainersDataRequestDataFile(fileName2, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
        postCreateContainer(flow, hashcodeContainersDataRequest(List.of(dataFile1, dataFile2)));

        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(MANIFEST, containerBase64);
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);
        XmlPath hashcodesSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertThat(manifest.get("manifest:manifest.manifest:file-entry[1].@manifest:full-path"), is(fileName1));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[2].@manifest:full-path"), is(fileName2));

        assertThat(hashcodesSha256.get("hashcodes.file-entry[0].@full-path"), is(fileName1));
        assertThat(hashcodesSha256.get("hashcodes.file-entry[1].@full-path"), is(fileName2));

        assertThat(hashcodesSha512.get("hashcodes.file-entry[0].@full-path"), is(fileName1));
        assertThat(hashcodesSha512.get("hashcodes.file-entry[1].@full-path"), is(fileName2));
    }

    @Test
    void uploadHashcodeContainerAndVerifyFileOrder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFileOrderSameInManifestSha512Sha256.asice"));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(MANIFEST, containerBase64);
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);
        XmlPath hashcodesSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertThat(manifest.get("manifest:manifest.manifest:file-entry[1].@manifest:full-path"), is("1 GIF testfail.gif"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[2].@manifest:full-path"), is("2 JPG testfail.jpg"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[3].@manifest:full-path"), is("3 PNG testfail.png"));

        assertThat(hashcodesSha256.get("hashcodes.file-entry[0].@full-path"), is("1 GIF testfail.gif"));
        assertThat(hashcodesSha256.get("hashcodes.file-entry[1].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodesSha256.get("hashcodes.file-entry[2].@full-path"), is("3 PNG testfail.png"));

        assertThat(hashcodesSha512.get("hashcodes.file-entry[0].@full-path"), is("1 GIF testfail.gif"));
        assertThat(hashcodesSha512.get("hashcodes.file-entry[1].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodesSha512.get("hashcodes.file-entry[2].@full-path"), is("3 PNG testfail.png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hashcodeFileOrderDifferentInSha256", "hashcodeFileOrderDifferentInSha512AndSha512FirstInZip"})
    void uploadHashcodeContainerFileOrderDifferentInFirstShaFileAndVerifyFileOrder(String fileName) throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(fileName + ".asice"));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(MANIFEST, containerBase64);
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);
        XmlPath hashcodesSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertThat(manifest.get("manifest:manifest.manifest:file-entry[1].@manifest:full-path"), is("2 JPG testfail.jpg"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[2].@manifest:full-path"), is("3 PNG testfail.png"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[3].@manifest:full-path"), is("1 GIF testfail.gif"));

        assertThat(hashcodesSha256.get("hashcodes.file-entry[0].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodesSha256.get("hashcodes.file-entry[1].@full-path"), is("3 PNG testfail.png"));
        assertThat(hashcodesSha256.get("hashcodes.file-entry[2].@full-path"), is("1 GIF testfail.gif"));

        assertThat(hashcodesSha512.get("hashcodes.file-entry[0].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodesSha512.get("hashcodes.file-entry[1].@full-path"), is("3 PNG testfail.png"));
        assertThat(hashcodesSha512.get("hashcodes.file-entry[2].@full-path"), is("1 GIF testfail.gif"));
    }

    @Test
    void uploadHashcodeContainerFileOrderDifferentInSha512AndVerifyFileOrder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFileOrderDifferentInSha512.asice"));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(MANIFEST, containerBase64);
        XmlPath hashcodeSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);
        XmlPath hashcodeSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertThat(manifest.get("manifest:manifest.manifest:file-entry[1].@manifest:full-path"), is("1 GIF testfail.gif"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[2].@manifest:full-path"), is("2 JPG testfail.jpg"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[3].@manifest:full-path"), is("3 PNG testfail.png"));

        assertThat(hashcodeSha256.get("hashcodes.file-entry[0].@full-path"), is("1 GIF testfail.gif"));
        assertThat(hashcodeSha256.get("hashcodes.file-entry[1].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodeSha256.get("hashcodes.file-entry[2].@full-path"), is("3 PNG testfail.png"));

        assertThat(hashcodeSha512.get("hashcodes.file-entry[0].@full-path"), is("1 GIF testfail.gif"));
        assertThat(hashcodeSha512.get("hashcodes.file-entry[1].@full-path"), is("2 JPG testfail.jpg"));
        assertThat(hashcodeSha512.get("hashcodes.file-entry[2].@full-path"), is("3 PNG testfail.png"));
    }

    @Test
    void uploadHashcodeContainerFileOrderDifferentInManifestAndVerifyFileOrder() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeFileOrderDifferentInManifest.asice"));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(MANIFEST, containerBase64);
        XmlPath hashcodeSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);
        XmlPath hashcodeSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertThat(manifest.get("manifest:manifest.manifest:file-entry[1].@manifest:full-path"), is("test.txt"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[2].@manifest:full-path"), is("test1.txt"));
        assertThat(manifest.get("manifest:manifest.manifest:file-entry[3].@manifest:full-path"), is("test2.txt"));

        assertThat(hashcodeSha256.get("hashcodes.file-entry[0].@full-path"), is("test.txt"));
        assertThat(hashcodeSha256.get("hashcodes.file-entry[1].@full-path"), is("test1.txt"));
        assertThat(hashcodeSha256.get("hashcodes.file-entry[2].@full-path"), is("test2.txt"));

        assertThat(hashcodeSha512.get("hashcodes.file-entry[0].@full-path"), is("test.txt"));
        assertThat(hashcodeSha512.get("hashcodes.file-entry[1].@full-path"), is("test1.txt"));
        assertThat(hashcodeSha512.get("hashcodes.file-entry[2].@full-path"), is("test2.txt"));
    }

    protected Boolean fileExistsInContainer(String fileName, String containerBase64) {
        try {
            extractEntryFromContainer(fileName, containerBase64);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
