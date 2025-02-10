package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.DuplicateDataFileException;
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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.ADDITIONAL_MANIFEST_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.ADDITIONAL_SHA256_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.ADDITIONAL_SHA512_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_MANIFEST_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_MANIFEST_ORDER;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_SHA256_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_SHA256_ORDER;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_SHA512_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.DIFFERENT_SHA512_ORDER;
import static ee.openeid.siga.service.signature.test.RequestUtil.DUPLICATE_HASHCODE_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.DUPLICATE_MANIFEST_FILENAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE_INVALID_SIGNATURE_FILE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE_NONSTANDARD_SIGNATURE_FILE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE_SEVERAL_DATAFILES;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE_SEVERAL_DATAFILES_RANDOM_ORDER;
import static ee.openeid.siga.service.signature.test.RequestUtil.UNKNOWN_MIMETYPES_DEFINED_IN_MANIFEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashcodeContainerTest {

    @Test
    void validHashcodeContainerCreation() throws IOException {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeDataFiles.forEach(hashcodeContainer::addDataFile);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainerFilesHolder hashcodeContainerFilesHolder = TestUtil.getContainerFiles(outputStream.toByteArray());
            assertEquals(TestUtil.MIMETYPE, hashcodeContainerFilesHolder.getMimeTypeContent());
            assertEquals(TestUtil.MANIFEST_CONTENT, hashcodeContainerFilesHolder.getManifestContent());
            assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha256Content());
            assertEquals(TestUtil.HASHCODES_SHA512_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha512Content());
        }
    }

    @Test
    void validHashcodeContainerOpening() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE);
        hashcodeContainer.open(container);
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertFalse(StringUtils.isBlank(hashcodeContainer.getSignatures().get(0).getGeneratedSignatureId()));
        assertEquals(2, hashcodeContainer.getDataFiles().size());

        List<SignatureHashcodeDataFile> signatureDataFiles = hashcodeContainer.getSignatures().get(0).getDataFiles();
        assertEquals(2, signatureDataFiles.size());
        assertEquals("test.txt", signatureDataFiles.get(0).getFileName());
        assertEquals("SHA256", signatureDataFiles.get(0).getHashAlgo());
        assertEquals("test1.txt", signatureDataFiles.get(1).getFileName());
        assertEquals("SHA256", signatureDataFiles.get(1).getHashAlgo());
    }

    @Test
    void validHashcodeContainerOpeningWithDataFilesInGrowingOrder() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE_SEVERAL_DATAFILES);
        hashcodeContainer.open(container);
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertEquals(10, hashcodeContainer.getDataFiles().size());
        assertEquals("test.txt", hashcodeContainer.getDataFiles().get(0).getFileName());
        assertEquals("test1.txt", hashcodeContainer.getDataFiles().get(1).getFileName());
        assertEquals("test2.txt", hashcodeContainer.getDataFiles().get(2).getFileName());
        assertEquals("test3.txt", hashcodeContainer.getDataFiles().get(3).getFileName());
        assertEquals("test4.txt", hashcodeContainer.getDataFiles().get(4).getFileName());
        assertEquals("test5.txt", hashcodeContainer.getDataFiles().get(5).getFileName());
        assertEquals("test6.txt", hashcodeContainer.getDataFiles().get(6).getFileName());
        assertEquals("test7.txt", hashcodeContainer.getDataFiles().get(7).getFileName());
        assertEquals("test8.txt", hashcodeContainer.getDataFiles().get(8).getFileName());
        assertEquals("test9.txt", hashcodeContainer.getDataFiles().get(9).getFileName());
    }

    @Test
    void validHashcodeContainerOpeningWithDataFilesInRandomOrder() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE_SEVERAL_DATAFILES_RANDOM_ORDER);
        hashcodeContainer.open(container);
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertEquals(10, hashcodeContainer.getDataFiles().size());
        assertEquals("test9.txt", hashcodeContainer.getDataFiles().get(0).getFileName());
        assertEquals("test.txt", hashcodeContainer.getDataFiles().get(1).getFileName());
        assertEquals("test1.txt", hashcodeContainer.getDataFiles().get(2).getFileName());
        assertEquals("test6.txt", hashcodeContainer.getDataFiles().get(3).getFileName());
        assertEquals("test3.txt", hashcodeContainer.getDataFiles().get(4).getFileName());
        assertEquals("test4.txt", hashcodeContainer.getDataFiles().get(5).getFileName());
        assertEquals("test5.txt", hashcodeContainer.getDataFiles().get(6).getFileName());
        assertEquals("test2.txt", hashcodeContainer.getDataFiles().get(7).getFileName());
        assertEquals("test7.txt", hashcodeContainer.getDataFiles().get(8).getFileName());
        assertEquals("test8.txt", hashcodeContainer.getDataFiles().get(9).getFileName());

    }

    @Test
    void hashcodeContainer_OpenWithNonstandardSignatureFileName_SignaturesAreFound() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE_NONSTANDARD_SIGNATURE_FILE_NAME);
        hashcodeContainer.open(container);
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertFalse(StringUtils.isBlank(hashcodeContainer.getSignatures().get(0).getGeneratedSignatureId()));
        assertEquals(2, hashcodeContainer.getDataFiles().size());

        List<SignatureHashcodeDataFile> signatureDataFiles = hashcodeContainer.getSignatures().get(0).getDataFiles();
        assertEquals(2, signatureDataFiles.size());
        assertEquals("test.txt", signatureDataFiles.get(0).getFileName());
        assertEquals("SHA256", signatureDataFiles.get(0).getHashAlgo());
        assertEquals("test1.txt", signatureDataFiles.get(1).getFileName());
        assertEquals("SHA256", signatureDataFiles.get(1).getHashAlgo());
    }

    @Test
    void hashcodeContainer_OpenWithInvalidSignatureFileName_SignaturesAreNotFound() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE_INVALID_SIGNATURE_FILE_NAME);
        hashcodeContainer.open(container);
        assertEquals(0, hashcodeContainer.getSignatures().size());
    }

    @Test
    void hashcodeSha256ContainsDifferentFileName() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_SHA256_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Hashcode container is missing SHA512 hash", caughtException.getMessage());
    }

    @Test
    void hashcodeSha512ContainsDifferentFileName() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_SHA512_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Hashcode container is missing SHA512 hash", caughtException.getMessage());
    }

    @Test
    void hashcodeSha256ContainsAdditionalFileName() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(ADDITIONAL_SHA256_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Hashcode container is missing SHA512 hash", caughtException.getMessage());
    }

    @Test
    void hashcodeSha512ContainsAdditionalFileName() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(ADDITIONAL_SHA512_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Hashcode container is missing SHA256 hash", caughtException.getMessage());
    }

    @Test
    void hashcodeDataFileContainsDuplicateFileNames() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DUPLICATE_HASHCODE_FILENAME);

        DuplicateDataFileException caughtException = assertThrows(
                DuplicateDataFileException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Hashcodes data file contains duplicate entry: test.txt", caughtException.getMessage());
    }

    @Test
    void manifestContainsDuplicateFileNames() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DUPLICATE_MANIFEST_FILENAME);

        DuplicateDataFileException caughtException = assertThrows(
                DuplicateDataFileException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("duplicate entry in manifest file: test.txt", caughtException.getMessage());
    }

    @Test
    void manifestAndHashcodeFilesMustContainMatchingFileNames() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_MANIFEST_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Manifest does not contain same file names as hashcode files", caughtException.getMessage());
    }

    @Test
    void manifestContainsAdditionalFileName() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(ADDITIONAL_MANIFEST_FILENAME);

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Manifest does not contain same file names as hashcode files", caughtException.getMessage());
    }

    @Test
    void hashcodeSha256HasDifferentFileNameOrder() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_SHA256_ORDER);
        hashcodeContainer.open(container);
        assertEquals(3, hashcodeContainer.getDataFiles().size());
        assertEquals("test2.txt", hashcodeContainer.getDataFiles().get(0).getFileName());
        assertEquals("test.txt", hashcodeContainer.getDataFiles().get(1).getFileName());
        assertEquals("test1.txt", hashcodeContainer.getDataFiles().get(2).getFileName());
    }

    @Test
    void hashcodeSha512HasDifferentFileNameOrder() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_SHA512_ORDER);
        hashcodeContainer.open(container);
        assertEquals(3, hashcodeContainer.getDataFiles().size());
        assertEquals("test.txt", hashcodeContainer.getDataFiles().get(0).getFileName());
        assertEquals("test1.txt", hashcodeContainer.getDataFiles().get(1).getFileName());
        assertEquals("test2.txt", hashcodeContainer.getDataFiles().get(2).getFileName());
    }

    @Test
    void manifestHasDifferentFileNameOrder() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(DIFFERENT_MANIFEST_ORDER);
        hashcodeContainer.open(container);
        assertEquals(3, hashcodeContainer.getDataFiles().size());
        assertEquals("test.txt", hashcodeContainer.getDataFiles().get(0).getFileName());
        assertEquals("test1.txt", hashcodeContainer.getDataFiles().get(1).getFileName());
        assertEquals("test2.txt", hashcodeContainer.getDataFiles().get(2).getFileName());
    }

    @Test
    void shouldUseExistingMimetypeFromManifestForUnknownMimetype() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(UNKNOWN_MIMETYPES_DEFINED_IN_MANIFEST);
        hashcodeContainer.open(container);
        HashcodeContainer newContainer;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            newContainer = new HashcodeContainer();
            newContainer.open(outputStream.toByteArray());
        }

        assertEquals(2, newContainer.getDataFiles().size());
        assertEquals("text_file.txt", newContainer.getDataFiles().get(0).getFileName());
        assertEquals("text/plain", newContainer.getDataFiles().get(0).getMimeType());
        assertEquals("word_file.docx", newContainer.getDataFiles().get(1).getFileName());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", newContainer.getDataFiles().get(1).getMimeType());
    }

    @Test
    void couldNotAddDataFileWhenSignatureExists() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile(SIGNED_HASHCODE);
        hashcodeContainer.open(container);
        HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
        hashcodeDataFile.setFileName("randomFile.txt");
        hashcodeDataFile.setFileHashSha256("asdasd=");

        SignatureExistsException caughtException = assertThrows(
            SignatureExistsException.class, () -> hashcodeContainer.addDataFile(hashcodeDataFile)
        );
        assertEquals("Unable to add data file when signature exists", caughtException.getMessage());
    }

    @Test
    void hashcodeContainerMustHaveAtLeastOneDataFile() throws IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] outputBytes;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            outputBytes = outputStream.toByteArray();
        }
        HashcodeContainer newHashcodeContainer = new HashcodeContainer();

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> newHashcodeContainer.open(outputBytes)
        );
        assertEquals("Container must have data file hashes", caughtException.getMessage());
    }

    @Test
    void hashcodeContainerMissingSha512() {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(TestUtil.getFile("hashcodeMissingSha512File.asice"))
        );
        assertEquals("Hashcode container is missing SHA512 hash", caughtException.getMessage());
    }

    @Test
    void directoryNotAllowedForFileName() {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(TestUtil.getFile("hashcodeShaFileIsDirectory.asice"))
        );
        assertEquals("Hashcode container contains invalid file name", caughtException.getMessage());
    }

    @Test
    void containerWithTooLargeFiles() {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();

        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> hashcodeContainer.open(TestUtil.getFile("hashcodeWithBigHashcodesFile.asice"))
        );
        assertEquals("Container contains file which is too large", caughtException.getMessage());
    }

    @Test
    void validHashcodeContainerAddedNewData() throws IOException, URISyntaxException {
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
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertEquals(3, hashcodeContainer.getDataFiles().size());
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainer newContainer = new HashcodeContainer();
            newContainer.open(outputStream.toByteArray());
            assertEquals(1, newContainer.getSignatures().size());
            assertEquals(3, newContainer.getDataFiles().size());
        }
    }

    @Test
    void validHashcodeContainerOpening_StoredAlgoWithDataDescriptor() throws IOException, URISyntaxException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(TestUtil.getFile("hashcodeStoredAlgoWithDataDescriptor.asice"));
        assertEquals(1, hashcodeContainer.getSignatures().size());
        assertFalse(StringUtils.isBlank(hashcodeContainer.getSignatures().get(0).getGeneratedSignatureId()));
        assertEquals(1, hashcodeContainer.getDataFiles().size());

        List<SignatureHashcodeDataFile> signatureDataFiles = hashcodeContainer.getSignatures().get(0).getDataFiles();
        assertEquals(1, signatureDataFiles.size());
        assertEquals("client_test.go", signatureDataFiles.get(0).getFileName());
        assertEquals("SHA512", signatureDataFiles.get(0).getHashAlgo());
    }

    @Test
    void validHashcodeContainerCreation_withOneDataFile() throws IOException {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        hashcodeDataFiles.get(0).setFileHashSha512(null);
        HashcodeContainer hashcodeContainer = new HashcodeContainer(ServiceType.PROXY);
        hashcodeDataFiles.forEach(hashcodeContainer::addDataFile);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            hashcodeContainer.save(outputStream);
            HashcodeContainerFilesHolder hashcodeContainerFilesHolder = TestUtil.getContainerFiles(outputStream.toByteArray());
            assertEquals(TestUtil.MIMETYPE, hashcodeContainerFilesHolder.getMimeTypeContent());
            assertEquals(TestUtil.MANIFEST_CONTENT, hashcodeContainerFilesHolder.getManifestContent());
            assertEquals(TestUtil.HASHCODES_SHA256_CONTENT, hashcodeContainerFilesHolder.getHashcodesSha256Content());
            assertNull(hashcodeContainerFilesHolder.getHashcodesSha512Content());
        }
    }

    @Test
    void validateHashcodeContainerOpening_withInvalidBase64DataFileHash() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_with_invalid_base64_hash.asice");

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Invalid data file hash", caughtException.getMessage());
    }

    @Test
    void validateHashcodeContainerOpening_withInvalidLengthDataFileHash() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_with_invalid_length_hash.asice");

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Invalid data file hash", caughtException.getMessage());
    }

    @Test
    void invalidStructureHashcodeContainer() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("hashcode_invalid_structure.asice");

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Invalid hashcode container. Invalid file or directory in root level. Only mimetype file and META-INF directory allowed", caughtException.getMessage());
    }

    @Test
    void openRegularAsicContainerWithDataFiles() throws URISyntaxException, IOException {
        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        byte[] container = TestUtil.getFile("test.asice");

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> hashcodeContainer.open(container)
        );
        assertEquals("Invalid hashcode container. Invalid file or directory in root level. Only mimetype file and META-INF directory allowed", caughtException.getMessage());
    }

}
