package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.service.signature.configuration.DigiDoc4jConfigurationProperties;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import ee.openeid.siga.session.SessionService;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.Signature;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.X509Cert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.openeid.siga.service.signature.test.RequestUtil.CLIENT_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_SESSION_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.ESEAL_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_UUID;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.TestUtil.createSignedContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.pkcs12Esteid2018SignatureToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;


@ExtendWith(MockitoExtension.class)
class AugmentationValidationServiceTest {
    private static final Map<String, String> notAugmentableContainers = Map.of(
            "B_EPES", "bdoc-with-b-epes-signature.bdoc", // Contains a single B_EPES signature
            "LT_TM", "bdoc-with-tm-and-ts-signature.bdoc", // Contains one LT and one LT_TM signature
            "T", "T_level_signature.asice" // Contains a single T level signature (LT signature with removed OCSP)
    );
    @InjectMocks
    private AugmentationValidationService validationService;
    @Mock
    private SessionService sessionService;
    @Spy
    private Configuration euConfiguration = Configuration.of(Configuration.Mode.TEST);
//    @Mock
//    private DigiDoc4jConfigurationProperties dd4jConfigurationProperties;

    @BeforeEach
    void setUp() {
        euConfiguration.setValidationPolicy("dss-constraint.xml");
    }

    @Test
    void containerWithLtSignature_ReturnsSignature() {
        AsicContainerSession session = getContainerSession(TestUtil.createSignedContainer(SignatureProfile.LT));
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        Container container = ContainerUtil.createContainer(session.getContainer(), Configuration.of(Configuration.Mode.TEST));

        List<Signature> augmentableSignatures = validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container);

        assertEquals(1, augmentableSignatures.size());
        assertEquals(container.getSignatures().get(0), augmentableSignatures.get(0),
                "Returned signature does not point to the exact same object as original signature");
    }

    @Test
    void containerWithLtaSignature_ReturnsSignature() {
        AsicContainerSession session = getContainerSession(TestUtil.createSignedContainer(SignatureProfile.LTA));
        Container container = ContainerUtil.createContainer(session.getContainer(), Configuration.of(Configuration.Mode.TEST));
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        List<Signature> augmentableSignatures = validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container);

        assertEquals(1, augmentableSignatures.size());
        assertEquals(container.getSignatures().get(0), augmentableSignatures.get(0),
                "Returned signature does not point to the exact same object as original signature");
    }

    @Test
    void containerWithNotAugmentableSignatures_Fails() {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container)
        );

        assertEquals("Unable to augment. Container does not contain any signatures", caughtException.getMessage());
    }

    @Test
    void containerWithExpiredOcsp_ReturnsSignature() throws IOException, URISyntaxException {
        AsicContainerSession session = RequestUtil.createAsicSessionHolder();
        session.setContainer(TestUtil.getFile(VALID_ASICE_WITH_EXPIRED_OCSP));
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);
        Container container = ContainerUtil.createContainer(session.getContainer(), Configuration.of(Configuration.Mode.TEST));

        List<Signature> augmentableSignatures = validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container);

        assertEquals(1, augmentableSignatures.size());
        assertEquals(container.getSignatures().get(0), augmentableSignatures.get(0),
                "Returned signature does not point to the exact same object as original signature");
    }

    @Test
    void containerWithBLevelSignature_Fails() {
        Container container = TestUtil.createSignedContainer(SignatureProfile.B_BES);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container)
        );

        assertEquals("Cannot augment signature profile B_BES", caughtException.getMessage());
    }

    @Test
    void containerWithOneLtAndOneBSignature_Fails() {
        Container container = createSignedContainer(SignatureProfile.LT);
        SignatureBuilder builder = SignatureBuilder
                .aSignature(container)
                .withSignatureProfile(SignatureProfile.B_BES);
        org.digidoc4j.Signature signature = builder.withSignatureToken(pkcs12Esteid2018SignatureToken).invokeSigning();
        container.addSignature(signature);
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container)
        );

        assertEquals("Cannot augment signature profile B_BES", caughtException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"B_EPES", "LT_TM", "T"})
    void containerWithNotAugmentableSignatureProfile_Fails(String signatureProfile) throws URISyntaxException {
        String containerFilename = notAugmentableContainers.get(signatureProfile);
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(containerFilename).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container)
        );

        assertEquals("Cannot augment signature profile " + signatureProfile, caughtException.getMessage());
    }

    @Test
    void containerWithOnlyESeal_Fails() throws URISyntaxException {
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(ESEAL_WITH_EXPIRED_OCSP).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container)
        );

        assertEquals("Unable to augment. The only Estonian signatures in the container are e-Seals", caughtException.getMessage());
    }

    @Test
    void containerWithSignatureAndESeal_ReturnsSignatureOnly() throws URISyntaxException {
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource("LT_sig_and_LT_seal.asice").toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);
        Mockito.when(sessionService.getContainer(any())).thenReturn(session);

        List<Signature> augmentableSignatures = validationService.validateAndGetAugmentableSignatures(CONTAINER_ID, container);

        assertEquals(1, augmentableSignatures.size());
        assertEquals("\"JÕEORG,JAAK-KRISTJAN,38001085718\"",
                augmentableSignatures.get(0).getSigningCertificate().getSubjectName(X509Cert.SubjectName.CN));
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
