package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import io.restassured.path.xml.XmlPath;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.ContainerUtil.hashcodeDataFileAsXmlPath;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashcodeContainerStructureT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createHashcodeContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
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
    public void uploadHashcodeContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, hashcodeContainerRequestFromFile(DEFAULT_HASHCODE_CONTAINER_NAME));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA256, containerBase64));
        assertTrue(fileExistsInContainer(HASHCODE_SHA512, containerBase64));
        assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    public void createHashcodeContainerAndVerifyHascode256File() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);

        assertEquals(DEFAULT_FILENAME, hashcodesSha256.getString("hashcodes.file-entry[0].@full-path"));
        assertEquals(DEFAULT_SHA256_DATAFILE, hashcodesSha256.getString("hashcodes.file-entry[0].@hash"));
        assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha256.getString("hashcodes.file-entry[0].@size"));
    }

    @Test
    public void createHashcodeContainerAndVerifyHascode512File() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        XmlPath hashcodesSha512 = hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);

        assertEquals(DEFAULT_FILENAME, hashcodesSha512.getString("hashcodes.file-entry[0].@full-path"));
        assertEquals(DEFAULT_SHA512_DATAFILE, hashcodesSha512.getString("hashcodes.file-entry[0].@hash"));
        assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha512.getString("hashcodes.file-entry[0].@size"));
    }

    @Test
    public void createHashcodeContainerAndVerifyMimetypeFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, hashcodeContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        String mimeType = new String(extractEntryFromContainer(MIMETYPE, containerBase64));

        assertEquals("application/vnd.etsi.asic-e+zip", mimeType);
    }

    protected Boolean fileExistsInContainer(String fileName, String containerBase64) {
        try {
            extractEntryFromContainer(fileName, containerBase64);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
