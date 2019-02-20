package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.service.signature.hashcode.HashCodeContainer;
import ee.openeid.siga.service.signature.test.HashCodeContainerFilesHolder;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class HashCodeContainerTest {

    @Test
    public void validHashCodeContainerCreation() throws IOException {
        CreateContainerRequest request = RequestUtil.getHashCodeCreateContainerRequest();
        HashCodeContainer hashCodeContainer = new HashCodeContainer();
        request.getDataFiles().forEach(dataFile -> hashCodeContainer.getDataFiles().add(dataFile));
        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            hashCodeContainer.save(outputStream);
            HashCodeContainerFilesHolder hashCodeContainerFilesHolder = TestUtil.getContainerFiles(((ByteArrayOutputStream) outputStream).toByteArray());
            Assert.assertEquals(TestUtil.MIMETYPE, hashCodeContainerFilesHolder.getMimeTypeContent());
            Assert.assertEquals(TestUtil.MANIFEST_CONTENT, hashCodeContainerFilesHolder.getManifestContent());
            Assert.assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashCodeContainerFilesHolder.getHashCodesSha256Content());
            Assert.assertEquals(TestUtil.HASHCODES_SHA512_CONTENT, hashCodeContainerFilesHolder.getHashCodesSha512Content());
        }
    }
}
