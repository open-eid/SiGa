package ee.openeid.siga.test.asic;

import ee.openeid.siga.test.helper.AssumingProfileActive;
import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.extractEntryFromContainer;
import static ee.openeid.siga.test.utils.DigestSigner.signDigest;
import static ee.openeid.siga.test.utils.RequestBuilder.*;

public class AsicContainerStructureT extends TestBase {

    @ClassRule
    public static AssumingProfileActive assumingRule = new AssumingProfileActive("datafileContainer");

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient1Service1();
    }

    @Test
    public void createAsicContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        CreateContainerRemoteSigningResponse dataToSignResponse = postRemoteSigningInSession(flow, remoteSigningRequestWithDefault(SIGNER_CERT_PEM, "LT")).as(CreateContainerRemoteSigningResponse.class);
        putRemoteSigningInSession(flow, remoteSigningSignatureValueRequest(signDigest(dataToSignResponse.getDataToSign(), dataToSignResponse.getDigestAlgorithm())), dataToSignResponse.getGeneratedSignatureId());

        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        Assert.assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        Assert.assertTrue(fileExistsInContainer(DEFAULT_FILENAME, containerBase64));
        Assert.assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        Assert.assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    public void uploadAsicContainerAndVerifyStructure() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postUploadContainer(flow, asicContainerRequestFromFile(DEFAULT_ASICE_CONTAINER_NAME));
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();

        Assert.assertTrue(fileExistsInContainer(MANIFEST, containerBase64));
        Assert.assertTrue(fileExistsInContainer(UPLOADED_FILENAME, containerBase64));
        Assert.assertTrue(fileExistsInContainer("test.xml", containerBase64));
        Assert.assertTrue(fileExistsInContainer(MIMETYPE, containerBase64));
        Assert.assertTrue(fileExistsInContainer("META-INF/signatures0.xml", containerBase64));
    }

    @Test
    public void createAsicContainerAndVerifyMimetypeFile() throws JSONException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        postCreateContainer(flow, asicContainersDataRequestWithDefault());
        String containerBase64 = getContainer(flow).getBody().path(CONTAINER).toString();
        String mimeType = new String(extractEntryFromContainer(MIMETYPE, containerBase64));

        Assert.assertEquals("application/vnd.etsi.asic-e+zip", mimeType);
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
