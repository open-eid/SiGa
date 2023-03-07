package ee.openeid.siga.service.signature.container.hashcode;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.Signature;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionService;
import eu.europa.esig.dss.model.MimeType;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ee.openeid.siga.service.signature.test.RequestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class HashcodeContainerServiceTest {

    @Spy
    @InjectMocks
    private HashcodeContainerService containerService;

    @Mock
    private SessionService sessionService;

    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);

    @BeforeEach
    public void setUp() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.lenient().when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f").build());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        Mockito.lenient().doReturn(CONTAINER_ID).when(containerService).generateContainerId();
        Mockito.lenient().when(sessionService.getSessionId(CONTAINER_ID)).thenReturn(CONTAINER_SESSION_ID);
    }

    @Test
    public void successfulCreateContainer() {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        String containerId = containerService.createContainer(hashcodeDataFiles);
        assertFalse(StringUtils.isBlank(containerId));

        verifySessionServiceUpdateCalled(containerId, session -> {
            assertNotNull(session.getDataFiles());
            assertEquals(hashcodeDataFiles.size(), session.getDataFiles().size());
            for (int i = 0; i < hashcodeDataFiles.size(); ++i) {
                assertEquals(MimeType.fromFileName(hashcodeDataFiles.get(i).getFileName()).getMimeTypeString(), session.getDataFiles().get(i).getMimeType());
            }
        });
    }

    @Test
    public void successfulCreateContainerFromDataFileWithUnknownExtension() {
        List<HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles().stream().limit(1)
                .peek(dataFile -> dataFile.setFileName("filename.unknown")).collect(Collectors.toList());
        String containerId = containerService.createContainer(hashcodeDataFiles);
        assertFalse(StringUtils.isBlank(containerId));

        verifySessionServiceUpdateCalled(containerId, session -> {
            assertEquals(MimeType.BINARY.getMimeTypeString(), session.getDataFiles().get(0).getMimeType());
        });
    }

    @Test
    public void successfulUploadContainer() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(SIGNED_HASHCODE).readAllBytes()));
        String containerId = containerService.uploadContainer(container);
        assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    public void successfulGetContainer() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createHashcodeSessionHolder());
        String container = containerService.getContainer(CONTAINER_ID);
        assertFalse(StringUtils.isBlank(container));
    }

    @Test
    public void successfulGetDataFiles() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createHashcodeSessionHolder());
        List<HashcodeDataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);
        assertEquals("test.txt", dataFiles.get(0).getFileName());
        assertEquals(Integer.valueOf(10), dataFiles.get(0).getFileSize());
        assertEquals("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg", dataFiles.get(0).getFileHashSha256());
        assertEquals("gRKArS6jBsPLF1VP7aQ8VZ7BA5QA66hj/ntmNcxONZG5899w2VFHg9psyEH4Scg7rPSJQEYf65BGAscMztSXsA", dataFiles.get(0).getFileHashSha512());
    }

    @Test
    public void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createHashcodeSessionHolder());
        List<Signature> signatures = containerService.getSignatures(CONTAINER_ID);
        assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatures.get(0).getId());
        assertEquals("LT", signatures.get(0).getSignatureProfile());
        assertFalse(StringUtils.isBlank(signatures.get(0).getGeneratedSignatureId()));
        assertEquals("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE", signatures.get(0).getSignerInfo());
    }

    @Test
    public void successfulGetSignature() throws IOException, URISyntaxException {
        HashcodeContainerSession session = createHashcodeSessionHolder();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        org.digidoc4j.Signature signature = containerService.getSignature(CONTAINER_ID, session.getSignatures().get(0).getGeneratedSignatureId());
        assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signature.getId());
    }

    @Test
    public void addDataFileButSignatureExists() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createHashcodeSessionHolder());

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.addDataFiles(CONTAINER_ID, createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Unable to add/remove data file. Container contains signature(s)", caughtException.getMessage());
    }

    @Test
    public void successfulAddDataFile() throws IOException, URISyntaxException {
        HashcodeContainerSession session = createHashcodeSessionHolder();

        session.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.addDataFiles(CONTAINER_ID, createHashcodeDataFileListWithOneFile("test1.txt"));
        assertEquals(Result.OK, result);
    }

    @Test
    public void successfulRemoveDataFile() throws IOException, URISyntaxException {
        HashcodeContainerSession session = createHashcodeSessionHolder();

        session.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.removeDataFile(CONTAINER_ID, "test.txt");
        assertEquals(Result.OK, result);
    }

    @Test
    public void removeDataFileNoDataFile() throws IOException, URISyntaxException {
        HashcodeContainerSession session = createHashcodeSessionHolder();
        session.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        ResourceNotFoundException caughtException = assertThrows(
            ResourceNotFoundException.class, () -> containerService.removeDataFile(CONTAINER_ID, "test.xml")
        );
        assertEquals("Data file named test.xml not found", caughtException.getMessage());
    }

    @Test
    public void successfulCloseSession() {
        Result result = containerService.closeSession(CONTAINER_ID);
        assertEquals(Result.OK, result);
    }

    @Test
    public void uploadContainerWithDuplicateDataFilesThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("hashcode_duplicate_data_files.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer(container)
        );
        assertEquals("Hashcodes data file contains duplicate entry: test1.txt", caughtException.getMessage());
    }

    @Test
    public void uploadContainerWithDuplicateDataFileInManifestThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("hashcode_duplicate_data_files_in_manifest.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer(container)
        );
        assertEquals("duplicate entry in manifest file: test.txt", caughtException.getMessage());
    }

    @Test
    public void uploadContainerWithDuplicateDataFilesInSignatureThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("hashcode_duplicate_data_files_in_signature.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer(container)
        );
        assertEquals("Signature contains duplicate data file: test1.txt", caughtException.getMessage());
    }

    @Test
    public void addDuplicateDataFileThrows() throws IOException, URISyntaxException {
        HashcodeContainerSession session = createHashcodeSessionHolder();
        session.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.addDataFiles(CONTAINER_ID, createHashcodeDataFileListWithOneFile())
        );
        assertEquals("Duplicate data files not allowed: test.txt", caughtException.getMessage());
    }

    private void verifySessionServiceUpdateCalled(String expectedContainerId, Consumer<HashcodeContainerSession> sessionValidator) {
        ArgumentCaptor<HashcodeContainerSession> sessionCaptor = ArgumentCaptor.forClass(HashcodeContainerSession.class);
        Mockito.verify(sessionService, Mockito.times(1)).update(sessionCaptor.capture());
        Mockito.verify(sessionService, Mockito.times(1)).getSessionId(eq(expectedContainerId));
        HashcodeContainerSession updatedSession = sessionCaptor.getValue();
        Mockito.verifyNoMoreInteractions(sessionService);

        sessionValidator.accept(updatedSession);
    }

    private byte[] getFile(String fileName) throws IOException, URISyntaxException {
        return TestUtil.getFileInputStream(fileName).readAllBytes();
    }

}
