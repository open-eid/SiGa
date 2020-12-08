package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.common.model.SignatureHashcodeDataFile;
import ee.openeid.siga.service.signature.test.HashcodeContainerFilesHolder;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;

public class HashcodeContainerTest {

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void validHashcodeContainerCreation() throws IOException {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeDataFiles.forEach(hashcodeContainer::addDataFile);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainerFilesHolder hashcodeContainerFilesHolder = TestUtil.getContainerFiles(outputStream.toByteArray());
            Assert.assertEquals(TestUtil.MIMETYPE, hashcodeContainerFilesHolder.getMimeTypeContent());
            Assert.assertEquals(TestUtil.MANIFEST_CONTENT, hashcodeContainerFilesHolder.getManifestContent());
            Assert.assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha256Content());
            Assert.assertEquals(TestUtil.HASHCODES_SHA512_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha512Content());
        }
    }

    @Test
    public void validHashcodeContainerOpening() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE);
        hashcodeContainer.open(container);
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


    @Test
    public void couldNotAddDataFileWhenSignatureExists() throws IOException, URISyntaxException {
        expectedEx.expect(SignatureExistsException.class);
        expectedEx.expectMessage("Unable to add data file when signature exists");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE);
        hashcodeContainer.open(container);
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName("randomFile.txt");
        hashcodeDataFile.setFileHashSha256("asdasd=");
        hashcodeContainer.addDataFile(hashcodeDataFile);
    }

    @Test
    public void hashcodeContainerMustHaveAtLeastOneDataFile() throws IOException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Container must have data file hashes");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainer newHashcodeContainer = new HashcodeContainer();
            newHashcodeContainer.open(outputStream.toByteArray());
        }
    }

    @Test
    public void hashcodeContainerMissingSha512() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Hashcode container is missing SHA512 hash");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodeMissingSha512File.asice"));
    }

    @Test
    public void directoryNotAllowedForFileName() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Hashcode container contains invalid file name");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodeShaFileIsDirectory.asice"));
    }

    @Test
    public void containerWithTooLargeFiles() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Container contains file which is too large");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodeWithBigHashcodesFile.asice"));
    }

    @Test
    public void validHashcodeContainerAddedNewData() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile(SIGNED_HASHCODE));
        HashcodeSignatureWrapper signature = hashcodeContainer.getSignatures().get(0);

        hashcodeContainer.getSignatures().remove(0);
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName("randomFile.txt");
        hashcodeDataFile.setFileHashSha256("n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=");
        hashcodeDataFile.setFileHashSha512("7iaw3Ur350mqGo7jwQrpkj9hiYB3Lkc/iBml1JQODbJ6wYX4oOHV+E+IvIh/1nsUNzLDBMxfqa2Ob1f1ACio/w==");
        hashcodeDataFile.setFileSize(10);
        hashcodeContainer.addDataFile(hashcodeDataFile);

        hashcodeContainer.addSignature(signature);
        Assert.assertEquals(1, hashcodeContainer.getSignatures().size());
        Assert.assertEquals(3, hashcodeContainer.getDataFiles().size());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainer newContainer = new HashcodeContainer();
            newContainer.open(outputStream.toByteArray());
            Assert.assertEquals(1, newContainer.getSignatures().size());
            Assert.assertEquals(3, newContainer.getDataFiles().size());
        }
    }

    @Test
    public void validHashcodeContainerOpening_StoredAlgoWithDataDescriptor() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodeStoredAlgoWithDataDescriptor.asice"));
        Assert.assertEquals(1, hashcodeContainer.getSignatures().size());
        Assert.assertFalse(StringUtils.isBlank(hashcodeContainer.getSignatures().get(0).getGeneratedSignatureId()));
        Assert.assertEquals(1, hashcodeContainer.getDataFiles().size());

        List<SignatureHashcodeDataFile> signatureDataFiles = hashcodeContainer.getSignatures().get(0).getDataFiles();
        Assert.assertEquals(1, signatureDataFiles.size());
        Assert.assertEquals("client_test.go", signatureDataFiles.get(0).getFileName());
        Assert.assertEquals("SHA512", signatureDataFiles.get(0).getHashAlgo());
    }

    @Test
    public void validHashcodeContainerCreation_withOneDataFile() throws IOException {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        hashcodeDataFiles.get(0).setFileHashSha512(null);
        HashcodeContainer hashcodeContainer = new HashcodeContainer(ServiceType.PROXY);
        hashcodeDataFiles.forEach(hashcodeContainer::addDataFile);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainerFilesHolder hashcodeContainerFilesHolder = TestUtil.getContainerFiles(outputStream.toByteArray());
            Assert.assertEquals(TestUtil.MIMETYPE, hashcodeContainerFilesHolder.getMimeTypeContent());
            Assert.assertEquals(TestUtil.MANIFEST_CONTENT, hashcodeContainerFilesHolder.getManifestContent());
            Assert.assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha256Content());
            Assert.assertNull(hashcodeContainerFilesHolder.getHashcodesSha512Content());
        }
    }

    @Test
    public void validateHashcodeContainerOpening_withInvalidBase64DataFileHash() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Invalid data file hash");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_with_invalid_base64_hash.asice");
        hashcodeContainer.open(container);
    }

    @Test
    public void validateHashcodeContainerOpening_withInvalidLengthDataFileHash() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Invalid data file hash");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_with_invalid_length_hash.asice");
        hashcodeContainer.open(container);
    }

    @Test
    public void invalidStructureHashcodeContainer() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Invalid hashcode container. Invalid file or directory in root level. Only mimetype file and META-INF directory allowed");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_invalid_structure.asice");
        hashcodeContainer.open(container);
    }

    @Test
    public void openRegularAsicContainerWithDataFiles() throws IOException, URISyntaxException {
        expectedEx.expect(InvalidContainerException.class);
        expectedEx.expectMessage("Invalid hashcode container. Invalid file or directory in root level. Only mimetype file and META-INF directory allowed");
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("test.asice");
        hashcodeContainer.open(container);
    }

}
