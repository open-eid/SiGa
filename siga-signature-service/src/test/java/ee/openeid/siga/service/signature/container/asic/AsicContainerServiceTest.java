package ee.openeid.siga.service.signature.container.asic;


import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.model.ContainerInfo;
import ee.openeid.siga.common.model.DataFile;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.Signature;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionService;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.SignatureProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static ee.openeid.siga.service.signature.test.RequestUtil.CLIENT_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_SESSION_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_UUID;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICS;
import static ee.openeid.siga.service.signature.test.RequestUtil.createAsicSessionHolder;
import static ee.openeid.siga.service.signature.test.RequestUtil.createDataFileListWithOneFile;
import static org.digidoc4j.Container.DocumentType.ASICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AsicContainerServiceTest {

    @Spy
    @InjectMocks
    private AsicContainerService containerService;

    @Mock
    private SessionService sessionService;
    @Mock
    private AsicContainerAugmentationService augmentationService;
    @Mock
    private SigaEventLogger eventLogger;

    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);

    @Captor
    private ArgumentCaptor<Session> sessionCaptor;

    @BeforeEach
    void setUp() {
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
    void successfulCreateContainer() {
        List<DataFile> dataFiles = createDataFileListWithOneFile();

        String containerId = containerService.createContainer("test.asice", dataFiles);
        assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    void successfulUploadContainer() throws Exception {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(VALID_ASICE).readAllBytes()));
        String containerId = containerService.uploadContainer("test.asice", container);
        assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    void successfulGetContainer() throws Exception {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        ContainerInfo containerInfo = containerService.getContainer(CONTAINER_ID);
        InputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(containerInfo.getContainer().getBytes()));
        Container container = ContainerBuilder.aContainer(ASICE).fromStream(inputStream).build();
        assertEquals(1, container.getSignatures().size());
    }

    @Test
    void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        List<Signature> signatures = containerService.getSignatures(CONTAINER_ID);
        assertEquals("id-8c2a30729f251c6cb8336844b97f0657", signatures.get(0).getId());
        assertEquals("LT", signatures.get(0).getSignatureProfile());
        assertFalse(StringUtils.isBlank(signatures.get(0).getGeneratedSignatureId()));
        assertEquals("SERIALNUMBER=11404176865, GIVENNAME=MÄRÜ-LÖÖZ, SURNAME=ŽÕRINÜWŠKY, CN=\"ŽÕRINÜWŠKY,MÄRÜ-LÖÖZ,11404176865\", OU=digital signature, O=ESTEID, C=EE", signatures.get(0).getSignerInfo());
    }

    @Test
    void successfulGetSignature() throws IOException, URISyntaxException {
        AsicContainerSession session = createAsicSessionHolder();
        AtomicReference<String> signatureId = new AtomicReference<>();
        session.getSignatureIdHolder().forEach((sigId, integer) -> signatureId.set(sigId));
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        org.digidoc4j.Signature signature = containerService.getSignature(CONTAINER_ID, signatureId.get());
        assertEquals("id-8c2a30729f251c6cb8336844b97f0657", signature.getId());
    }

    @Test
    void successfulGetDataFiles() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());
        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);
        assertEquals("test.txt", dataFiles.get(0).getFileName());
        assertEquals("c2VlIG9uIHRlc3RmYWls", dataFiles.get(0).getContent());
    }

    @Test
    void successfulGetDataFilesFromNonCompositeAsicsContainer() {
        Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("test content".getBytes(StandardCharsets.UTF_8),
                        "test.xml", "text/plain")).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);

        assertEquals(1, dataFiles.size());
        assertEquals("test.xml", dataFiles.get(0).getFileName());
        assertEquals("test content", new String(Base64.getDecoder().decode(dataFiles.get(0).getContent())));
    }

    @Test
    void successfulGetDataFilesFromCompositeAsicsContainer() throws IOException, URISyntaxException {
        Container container = TestUtil.getContainer(getFile(VALID_ASICS));
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);

        assertEquals(1, dataFiles.size());
        assertEquals("test.txt", dataFiles.get(0).getFileName());
        assertEquals("see on testfail", new String(Base64.getDecoder().decode(dataFiles.get(0).getContent())));
    }

    @Test
    void successfulGetDataFilesFrom2ndLevelOf3LevelNestedCompositeAsicsContainer() throws IOException, URISyntaxException {
        Container container = TestUtil.getContainer(getFile("1xTST-recursive-asics-datafile.asics"));
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        List<DataFile> dataFiles = containerService.getDataFiles(CONTAINER_ID);

        assertEquals(1, dataFiles.size());
        assertEquals("timestamped-3.asics", dataFiles.get(0).getFileName());
        assertEquals(12930, Base64.getDecoder().decode(dataFiles.get(0).getContent()).length);
    }

    @Test
    void addDataFileButSignatureExists() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createAsicSessionHolder());

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.addDataFiles(CONTAINER_ID, createDataFileListWithOneFile())
        );
        assertEquals("Unable to add/remove data file. Container contains signature(s)", caughtException.getMessage());
    }

    @Test
    void addDataFileToNonCompositeAsicsContainerFails() {
        Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("test content".getBytes(),
                        "test.xml", "text/plain")).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.addDataFiles(CONTAINER_ID, createDataFileListWithOneFile())
        );
        assertEquals("Cannot add datafile to specified container.", caughtException.getMessage());
    }

    @Test
    void addDataFileToCompositeAsicsContainerFails() throws IOException, URISyntaxException {
        Container container = TestUtil.getContainer(getFile(VALID_ASICS));
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.addDataFiles(CONTAINER_ID, createDataFileListWithOneFile())
        );
        assertEquals("Unable to add/remove data file. Container contains timestamp token(s)", caughtException.getMessage());
    }

    @Test
    void successfulAddDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain")).build();
        AsicContainerSession session = getContainerSession(container);

        container.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        List<DataFile> dataFiles = createDataFileListWithOneFile();
        dataFiles.get(0).setFileName("test.pdf");
        Result result = containerService.addDataFiles(CONTAINER_ID, dataFiles);
        assertEquals(Result.OK, result);
    }

    @Test
    void successfulRemoveDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test1.xml", "text/plain"))
                .withDataFile(new org.digidoc4j.DataFile("DxZzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZa".getBytes(), "test2.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.removeDataFile(CONTAINER_ID, "test1.xml");
        assertEquals(Result.OK, result);
    }

    @Test
    void removeDataFileNoDataFile() {
        Container container = ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml1", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        ResourceNotFoundException caughtException = assertThrows(
            ResourceNotFoundException.class, () -> {
                    Result result = containerService.removeDataFile(CONTAINER_ID, "test.xml");
                    assertEquals(Result.OK, result);
                }
        );
        assertEquals("Data file named test.xml not found", caughtException.getMessage());
    }

    @Test
    void removeDataFileFromAsicsContainer() {
        Container container = ContainerBuilder.aContainer(Container.DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("test content".getBytes(),
                        "test.xml", "text/plain")).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        Result result = containerService.removeDataFile(CONTAINER_ID, "test.xml");
        assertEquals(Result.OK, result);
    }

    @Test
    void removeDdocFileFromCompositeAsicsContainerFails() throws IOException, URISyntaxException {
        Container container = TestUtil.getContainer(getFile(VALID_ASICS));
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
            InvalidSessionDataException.class, () -> containerService.removeDataFile(CONTAINER_ID, "container.ddoc")
        );

        assertEquals("Unable to add/remove data file. Container contains timestamp token(s)", caughtException.getMessage());
    }

    @Test
    void removeInnerDataFileFromCompositeAsicsContainerFails() throws IOException, URISyntaxException {
        Container container = TestUtil.getContainer(getFile(VALID_ASICS));
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> containerService.removeDataFile(CONTAINER_ID, "test.txt")
        );

        assertEquals("Unable to add/remove data file. Container contains timestamp token(s)", caughtException.getMessage());
    }

    @Test
    void successfulAugmentContainer() {
        Container container = TestUtil.createSignedContainer(SignatureProfile.LTA);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        Mockito.when(augmentationService.augmentContainer(any(byte[].class), anyString())).thenReturn(container);

        Result result = containerService.augmentContainer(CONTAINER_ID);

        assertEquals(Result.OK, result);
        Mockito.verify(augmentationService).augmentContainer(eq(session.getContainer()), anyString());
        Mockito.verify(sessionService).update(sessionCaptor.capture());
        assertEquals(CONTAINER_SESSION_ID, sessionCaptor.getValue().getSessionId());
    }

    @Test
    void unsuccessfulAugmentContainerThrows() {
        Container container = TestUtil.createSignedContainer(SignatureProfile.LTA);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        Mockito.doThrow(new InvalidSessionDataException("Unable to augment")).when(augmentationService).augmentContainer(any(byte[].class), anyString());

        InvalidSessionDataException exception = assertThrows(
                InvalidSessionDataException.class, () -> containerService.augmentContainer(CONTAINER_ID)
        );

        assertEquals("Unable to augment", exception.getMessage());
        Mockito.verify(sessionService, times(0)).update(any(Session.class));
    }

    @Test
    void successfulCloseSession() {
        String result = containerService.closeSession(CONTAINER_ID);
        assertEquals(Result.OK.name(), result);
    }

    @Test
    void uploadContainerWithInvalidContainerTypeThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("container.ddoc")));

        InvalidContainerException caughtException = assertThrows(
            InvalidContainerException.class, () -> containerService.uploadContainer("container.ddoc", container)
        );
        assertEquals("Invalid container type: DDOC", caughtException.getMessage());
    }

    @Test
    void uploadContainerWithDuplicateDataFilesThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("asice_duplicate_data_files.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer("test.asice", container)
        );
        assertEquals("Container contains duplicate data file: readme.txt", caughtException.getMessage());
    }

    @Test
    void uploadContainerWithDuplicateDataFileInManifestThrows() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(getFile("asice_duplicate_data_files_in_manifest.asice")));

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.uploadContainer("test.asice", container)
        );
        assertEquals("duplicate entry in manifest file: test.xml", caughtException.getMessage());
    }

    @Test
    void addDuplicateDataFileThrows() {
        Container container = ContainerBuilder
                .aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        container.getSignatures().clear();
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        List<DataFile> dataFiles = createDataFileListWithOneFile();
        dataFiles.get(0).setFileName("test.xml");

        DuplicateDataFileException caughtException = assertThrows(
            DuplicateDataFileException.class, () -> containerService.addDataFiles(CONTAINER_ID, dataFiles)
        );
        assertEquals("Duplicate data files not allowed: test.xml", caughtException.getMessage());
    }

    private byte[] getFile(String fileName) throws IOException, URISyntaxException {
        return TestUtil.getFileInputStream(fileName).readAllBytes();
    }

    private static AsicContainerSession getContainerSession(Container container) {
        Map<String, Integer> signatureIdHolder = new HashMap<>();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        AsicContainerSession session = AsicContainerSession.builder()
                .sessionId(CONTAINER_SESSION_ID)
                .clientName(CLIENT_NAME)
                .serviceName(SERVICE_NAME)
                .serviceUuid(SERVICE_UUID)
                .signatureIdHolder(signatureIdHolder)
                .containerName("test.asice")
                .container(outputStream.toByteArray())
                .build();
        return session;
    }
}
