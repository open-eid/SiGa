package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureHashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.service.signature.test.DetachedDataFileContainerFilesHolder;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;

public class DetachedDataFileContainerTest {

    @Test
    public void validHashcodeContainerCreation() throws IOException {
        CreateHashcodeContainerRequest request = RequestUtil.getHashcodeCreateContainerRequest();
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        request.getDataFiles().forEach(dataFile -> hashcodeContainer.addDataFile(ContainerUtil.transformDataFileToHashcodeDataFile(dataFile)));
        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            DetachedDataFileContainerFilesHolder hashcodeContainerFilesHolder = TestUtil.getContainerFiles(((ByteArrayOutputStream) outputStream).toByteArray());
            Assert.assertEquals(TestUtil.MIMETYPE, hashcodeContainerFilesHolder.getMimeTypeContent());
            Assert.assertEquals(TestUtil.MANIFEST_CONTENT, hashcodeContainerFilesHolder.getManifestContent());
            Assert.assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha256Content());
            Assert.assertEquals(TestUtil.HASHCODES_SHA512_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha512Content());
        }
    }

    @Test
    public void validHashcodeContainerOpening() throws IOException, URISyntaxException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        hashcodeContainer.open(inputStream);
        Assert.assertEquals(1, hashcodeContainer.getSignatures().size());
        Assert.assertFalse(StringUtils.isBlank(hashcodeContainer.getSignatures().get(0).getGeneratedSignatureId()));
        Assert.assertEquals(2, hashcodeContainer.getDataFiles().size());

        List<SignatureHashcodeDataFile> signatureDataFiles = hashcodeContainer.getSignatures().get(0).getDataFiles();
        Assert.assertEquals(2, signatureDataFiles.size());
        Assert.assertEquals("test.txt", signatureDataFiles.get(0).getFileName());
        Assert.assertEquals("SHA256", signatureDataFiles.get(0).getHashAlgo());
        Assert.assertEquals("test1.txt", signatureDataFiles.get(1).getFileName());
        Assert.assertEquals("SHA256", signatureDataFiles.get(1).getHashAlgo());
    }


    @Test(expected = SignatureExistsException.class)
    public void couldNotAddDataFileWhenSignatureExists() throws IOException, URISyntaxException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        hashcodeContainer.open(inputStream);
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName("randomFile.txt");
        hashcodeDataFile.setFileHashSha256("asdasd=");
        hashcodeContainer.addDataFile(hashcodeDataFile);
    }

    @Test(expected = InvalidContainerException.class)
    public void hashcodeContainerMustHaveAtLeastOneDataFile() throws IOException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            DetachedDataFileContainer newHashcodeContainer = new DetachedDataFileContainer();
            ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) outputStream;
            newHashcodeContainer.open(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
        }
    }

    @Test(expected = InvalidContainerException.class)
    public void hashcodeContainerMissingSha512() throws IOException, URISyntaxException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        hashcodeContainer.open(TestUtil.getFileInputStream("hashcodeMissingSha512File.asice"));
    }

    @Test(expected = InvalidContainerException.class)
    public void directoryNotAllowedForFileName() throws IOException, URISyntaxException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        hashcodeContainer.open(TestUtil.getFileInputStream("hashcodeShaFileIsDirectory.asice"));
    }

    @Test
    public void validHashcodeContainerAddedNewData() throws IOException, URISyntaxException {
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        hashcodeContainer.open(inputStream);
        SignatureWrapper signature = hashcodeContainer.getSignatures().get(0);

        hashcodeContainer.getSignatures().remove(0);
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName("randomFile.txt");
        hashcodeDataFile.setFileHashSha256("asdasd=");
        hashcodeDataFile.setFileHashSha512("oasdokasdoask=");
        hashcodeDataFile.setFileSize(10);
        hashcodeContainer.addDataFile(hashcodeDataFile);

        hashcodeContainer.addSignature(signature);
        Assert.assertEquals(1, hashcodeContainer.getSignatures().size());
        Assert.assertEquals(3, hashcodeContainer.getDataFiles().size());
        try (OutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            DetachedDataFileContainer newContainer = new DetachedDataFileContainer();
            newContainer.open(new ByteArrayInputStream(((ByteArrayOutputStream) outputStream).toByteArray()));
            Assert.assertEquals(1, newContainer.getSignatures().size());
            Assert.assertEquals(3, newContainer.getDataFiles().size());
        }
    }
}
