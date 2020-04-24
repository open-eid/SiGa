package ee.openeid.siga.test.hashcode;

import ee.openeid.siga.test.helper.TestBase;
import ee.openeid.siga.test.model.SigaApiFlow;
import eu.europa.esig.dss.model.MimeType;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.ContainerUtil.*;
import static ee.openeid.siga.test.utils.RequestBuilder.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class SoapProxySpecificT extends TestBase {

    private SigaApiFlow flow;

    @Before
    public void setUp() {
        flow = SigaApiFlow.buildForTestClient2Service7();
    }

    @Test
    public void CreateHashcodeContainerWithoutSha512hashesForSoapProxy() throws Exception {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    public void CreateHashcodeContainerWithoutSha256hashesForSoapProxyReturnsError() throws Exception {
        Response response = postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, null, DEFAULT_SHA512_DATAFILE , DEFAULT_FILESIZE));

        expectError(response, 400, INVALID_REQUEST);
    }

    @Test
    public void uploadHashcodeContainerWithoutSha512hashesForSoapProxy() throws Exception {
        Response response = postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeMissingSha512File.asice"));

        response.then()
                .statusCode(200)
                .body(CONTAINER_ID + ".length()", equalTo(36));
    }

    @Test
    public void createContainerForSoapProxyContainsMinimalData() throws Exception {
        postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();

        XmlPath manifest = manifestAsXmlPath(extractEntryFromContainer(MANIFEST, containerBase64));
        XmlPath hashcodesSha256 = hashcodeDataFileAsXmlPath(HASHCODE_SHA256, containerBase64);

        Assert.assertEquals(DEFAULT_FILENAME, hashcodesSha256.getString("hashcodes.file-entry[0].@full-path"));
        Assert.assertEquals(DEFAULT_SHA256_DATAFILE, hashcodesSha256.getString("hashcodes.file-entry[0].@hash"));
        Assert.assertEquals(DEFAULT_FILESIZE.toString(), hashcodesSha256.getString("hashcodes.file-entry[0].@size"));
        Assert.assertEquals("text/plain", manifest.getString("manifest:manifest.manifest:file-entry[" + (1) + "].@manifest:media-type"));
    }

    @Test
    public void createContainerForSoapProxyDoNotContainSha512file() throws Exception {
        Boolean sha512NotPresent = false;
        postCreateContainer(flow, hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null , DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();
        try {
            hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);
        }
        catch (IllegalStateException e) {
            Assert.assertEquals("No entry META-INF/hashcodes-sha512.xml found", e.getMessage());
            sha512NotPresent = true;
        }
        Assert.assertTrue("Container has hashcodes-sha512.xml file", sha512NotPresent);
    }

    @Test
    public void addDataFileForUploadedContainerForSoapProxyDoNotContainSha512file() throws Exception {
        Boolean sha512NotPresent = false;
        postUploadContainer(flow, hashcodeContainerRequestFromFile("hashcodeWithoutSignature.asice"));
        addDataFile(flow, addDataFileToHashcodeRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, null, DEFAULT_FILESIZE));

        Response response = getContainer(flow);
        String containerBase64 = response.path(CONTAINER).toString();
        try {
            hashcodeDataFileAsXmlPath(HASHCODE_SHA512, containerBase64);
        }
        catch (IllegalStateException e) {
            Assert.assertEquals("No entry META-INF/hashcodes-sha512.xml found", e.getMessage());
            sha512NotPresent = true;
        }
        Assert.assertTrue("Container has hashcodes-sha512.xml file", sha512NotPresent);
    }






    @Override
    public String getContainerEndpoint() {
        return HASHCODE_CONTAINERS;
    }
}
