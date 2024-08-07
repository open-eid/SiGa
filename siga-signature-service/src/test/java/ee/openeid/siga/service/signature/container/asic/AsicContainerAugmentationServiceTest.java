package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.session.AsicContainerSession;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import eu.europa.esig.dss.spi.x509.tsp.TimestampToken;
import org.apache.commons.lang3.NotImplementedException;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.impl.asic.AsicSignature;
import org.digidoc4j.impl.asic.asice.AsicEContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
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
import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_SESSION_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.ESEAL_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SERVICE_UUID;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_LATVIAN_ASICE;
import static ee.openeid.siga.service.signature.test.TestUtil.createSignedContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.pkcs12Esteid2018SignatureToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith(MockitoExtension.class)
class AsicContainerAugmentationServiceTest {
    private static final Map<String, String> containersWithSingleNotAugmentableSignature = Map.of(
            "B_EPES", "bdoc-with-b-epes-signature.bdoc", // Contains a single B_EPES signature
            "LT_TM", "valid-bdoc-tm.bdoc", // Contains one LT and one LT_TM signature
            "T", "T_level_signature.asice" // Contains a single T level signature (LT signature with removed OCSP)
    );
    private AsicContainerAugmentationService augmentationService;
    @Spy
    private Configuration euConfiguration = Configuration.of(Configuration.Mode.TEST);
    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);
    @Mock
    private SigaEventLogger eventLogger;

    @BeforeEach
    void setUp() {
        euConfiguration.setValidationPolicy("dss-constraint.xml");
        augmentationService = new AsicContainerAugmentationService(
                configuration,
                euConfiguration,
                eventLogger
        );
    }

    @Test
    void containerWithLtSignature_ReturnsAugmentedAsice() {
        AsicContainerSession session = getContainerSession(TestUtil.createSignedContainer(SignatureProfile.LT));

        Container augmentedContainer = augmentationService.augmentContainer(session.getContainer());

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
    }

    @Test
    void containerWithLtaSignature_ReturnsAugmentedAsiceWithAdditionalArchivalTimestamp() {
        AsicContainerSession session = getContainerSession(TestUtil.createSignedContainer(SignatureProfile.LTA));

        Container augmentedContainer = augmentationService.augmentContainer(session.getContainer());

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(2, archiveTimestamps.size(), "The signature must contain 2 archive timestamps");
    }

    @Test
    void containerWithoutSignatures_Fails() {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        AsicContainerSession session = getContainerSession(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        assertEquals("Unable to augment. Container does not contain any signatures", caughtException.getMessage());
    }

    @Test
    void containerWithoutEstonianSignatures_Fails() throws IOException, URISyntaxException {
        AsicContainerSession session = RequestUtil.createAsicSessionHolder();
        session.setContainer(TestUtil.getFile(VALID_LATVIAN_ASICE));

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures", caughtException.getMessage());
    }

    @Test
    void containerWithExpiredOcsp_ReturnsAugmentedAsice() throws IOException, URISyntaxException {
        AsicContainerSession session = RequestUtil.createAsicSessionHolder();
        session.setContainer(TestUtil.getFile(VALID_ASICE_WITH_EXPIRED_OCSP));

        Container augmentedContainer = augmentationService.augmentContainer(session.getContainer());

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
    }

    @Test
    void containerWithBLevelSignature_Fails() {
        Container container = TestUtil.createSignedContainer(SignatureProfile.B_BES);
        AsicContainerSession session = getContainerSession(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures with LT or LTA profile", caughtException.getMessage());
    }

    @Test
    void containerWithOneLtAndOneBSignature_WrappedIntoAsics() {
        Container container = createSignedContainer(SignatureProfile.LT);
        SignatureBuilder builder = SignatureBuilder
                .aSignature(container)
                .withSignatureProfile(SignatureProfile.B_BES);
        org.digidoc4j.Signature signature = builder.withSignatureToken(pkcs12Esteid2018SignatureToken).invokeSigning();
        container.addSignature(signature);
        AsicContainerSession session = getContainerSession(container);

        NotImplementedException caughtException = assertThrows(
                NotImplementedException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        // TODO SIGA-855: Handle ASiC-S containers
        assertEquals("ASiC-S wrapping is not yet implemented!", caughtException.getMessage());
    }

    @Test
    void containerWithOneLtAndOneLtTmSignature_WrappedIntoAsics() throws URISyntaxException {
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);

        NotImplementedException caughtException = assertThrows(
                NotImplementedException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        // TODO SIGA-855: Handle ASiC-S containers
        assertEquals("ASiC-S wrapping is not yet implemented!", caughtException.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"B_EPES", "LT_TM", "T"})
    void containerWithNotAugmentableSignatureProfile_Fails(String signatureProfile) throws URISyntaxException {
        String containerFilename = containersWithSingleNotAugmentableSignature.get(signatureProfile);
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(containerFilename).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures with LT or LTA profile", caughtException.getMessage());
    }

    @Test
    void containerWithOnlyESeal_Fails() throws URISyntaxException {
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource(ESEAL_WITH_EXPIRED_OCSP).toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(session.getContainer())
        );

        assertEquals("Unable to augment. Container contains only e-seals, but no Estonian personal signatures", caughtException.getMessage());
    }

    @Test
    void containerWithSignatureAndESeal_OnlySignatureIsAugmented() throws URISyntaxException {
        Path containerPath = Paths.get(AsicContainerServiceTest.class.getClassLoader().getResource("LT_sig_and_LT_seal.asice").toURI());
        Container container = ContainerBuilder.aContainer().fromExistingFile(containerPath.toString()).build();
        AsicContainerSession session = getContainerSession(container);

        Container augmentedContainer = augmentationService.augmentContainer(session.getContainer());

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(2, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
        assertEquals(SignatureProfile.LT, augmentedContainer.getSignatures().get(1).getProfile());
    }

    private static AsicContainerSession getContainerSession(Container container) {
        Map<String, Integer> signatureIdHolder = new HashMap<>();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        container.save(outputStream);
        return AsicContainerSession.builder()
                .sessionId(CONTAINER_SESSION_ID)
                .clientName(CLIENT_NAME)
                .serviceName(SERVICE_NAME)
                .serviceUuid(SERVICE_UUID)
                .signatureIdHolder(signatureIdHolder)
                .containerName("test.asice")
                .container(outputStream.toByteArray())
                .build();
    }

    private List<TimestampToken> getSignatureArchiveTimestamps(Container container, int signatureIndex) {
        return ((AsicSignature) container.getSignatures().get(signatureIndex)).getOrigin().getDssSignature().getArchiveTimestamps();
    }
}
