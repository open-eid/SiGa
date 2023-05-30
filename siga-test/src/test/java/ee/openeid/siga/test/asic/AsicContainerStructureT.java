package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.EnabledIfSigaProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.CONTAINER;
import static ee.openeid.siga.test.helper.TestData.CONTAINERS;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_ASICE_CONTAINER_NAME;
import static ee.openeid.siga.test.helper.TestData.DEFAULT_FILENAME;
import static ee.openeid.siga.test.helper.TestData.MANIFEST;
import static ee.openeid.siga.test.helper.TestData.MIMETYPE;
import static ee.openeid.siga.test.helper.TestData.SIGNER_CERT_ESTEID2018_PEM;
import static ee.openeid.siga.test.helper.TestData.UPLOADED_FILENAME;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainerRequestFromFile;
import static ee.openeid.siga.test.utils.RequestBuilder.asicContainersDataRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningRequestWithDefault;
import static ee.openeid.siga.test.utils.RequestBuilder.remoteSigningSignatureValueRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSigaProfileActive("datafileContainer")
class AsicContainerStructureT extends TestBase {

    private SigaApiFlow flow;

    @BeforeEach
    void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    void createAsicContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_ESTEID2018_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        assertTrue(fileExistsInContainer(DEFAULT_FILENAME, containerBase64));
        assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    void uploadAsicContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        assertTrue(fileExistsInContainer(UPLOADED_FILENAME, containerBase64));
        assertTrue(fileExistsInContainer("test.xml", containerBase64));
        assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    void createAsicContainerAndVerifyMimetypeFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
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
        return CONTAINERS;
    }
}
