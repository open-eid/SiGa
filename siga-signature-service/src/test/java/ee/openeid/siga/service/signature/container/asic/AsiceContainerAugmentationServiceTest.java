package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.service.signature.test.TestUtil;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.spi.x509.tsp.TimestampToken;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.Timestamp;
import org.digidoc4j.impl.asic.AsicSignature;
import org.digidoc4j.impl.asic.asice.AsicEContainer;
import org.digidoc4j.impl.asic.asics.AsicSContainer;
import org.digidoc4j.impl.asic.asics.AsicSContainerTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static ee.openeid.siga.service.signature.test.RequestUtil.ESEAL_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.INVALID_ASICE_WITH_EXPIRED_SIGNER_AND_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICE_WITH_EXPIRED_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_BDOC_WITH_LT_TM_SIGNATURE;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_LATVIAN_ASICE;
import static ee.openeid.siga.service.signature.test.TestUtil.createSignedContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.getBytesFromContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.getContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.getFile;
import static ee.openeid.siga.service.signature.test.TestUtil.pkcs12Esteid2018SignatureToken;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith(MockitoExtension.class)
class AsiceContainerAugmentationServiceTest {
    private static final Map<String, String> containersWithSingleNotAugmentableSignature = Map.of(
            "B_EPES", "bdoc-with-b-epes-signature.bdoc", // Contains a single B_EPES signature
            "T", "T_level_signature.asice" // Contains a single T level signature (LT signature with removed OCSP)
    );
    private AsiceContainerAugmentationService augmentationService;
    @Spy
    private Configuration euConfiguration = Configuration.of(Configuration.Mode.TEST);
    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);
    @Mock
    private SigaEventLogger eventLogger;

    @BeforeEach
    void setUp() {
        euConfiguration.setValidationPolicy("dss-constraint.xml");
        augmentationService = new AsiceContainerAugmentationService(
                configuration,
                euConfiguration,
                eventLogger
        );
    }

    @Test
    void containerWithLtSignature_ReturnsAugmentedAsice() throws IOException {
        Container container = createSignedContainer(SignatureProfile.LT);
        byte[] containerBytes = getBytesFromContainer(container);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), "originalContainer");

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
    }

    @Test
    void containerWithLtaSignature_ReturnsAugmentedAsiceWithAdditionalArchivalTimestamp() throws IOException {
        Container container = createSignedContainer(SignatureProfile.LTA);
        byte[] containerBytes = getBytesFromContainer(container);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), "originalContainer");

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(2, archiveTimestamps.size(), "The signature must contain 2 archive timestamps");
    }

    @Test
    void containerWithoutSignatures_Fails() throws IOException {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] containerBytes = getBytesFromContainer(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () ->
                        augmentationService.augmentContainer(containerBytes, getContainer(containerBytes), "originalContainer")
        );

        assertEquals("Unable to augment. Container does not contain any signatures", caughtException.getMessage());
    }

    @Test
    void containerWithoutEstonianSignatures_Fails() throws IOException, URISyntaxException {
        byte[] containerBytes = getFile(VALID_LATVIAN_ASICE);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () ->
                        augmentationService.augmentContainer(containerBytes, getContainer(containerBytes), VALID_LATVIAN_ASICE)
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures", caughtException.getMessage());
    }

    @Test
    void containerWithLtTmSignature_WrappedIntoAsics() throws URISyntaxException, IOException {
        byte[] containerBytes = getFile(VALID_BDOC_WITH_LT_TM_SIGNATURE);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), VALID_BDOC_WITH_LT_TM_SIGNATURE);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(1, augmentedContainer.getTimestamps().size());
        Timestamp timestamp = augmentedContainer.getTimestamps().get(0);
        assertEquals(AsicSContainerTimestamp.class, timestamp.getClass());
        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals(VALID_BDOC_WITH_LT_TM_SIGNATURE, dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(containerBytes, dataFile.getBytes(), "The original container included in the resulting ASiC-S container must not be modified.");
    }

    @Test
    void containerWithExpiredOcsp_ReturnsAugmentedAsice() throws IOException, URISyntaxException {
        byte[] containerBytes = getFile(VALID_ASICE_WITH_EXPIRED_OCSP);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), VALID_ASICE_WITH_EXPIRED_OCSP);

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
    }

    @Test
    void containerWithBLevelSignature_Fails() throws IOException {
        Container container = TestUtil.createSignedContainer(SignatureProfile.B_BES);
        byte[] containerBytes = getBytesFromContainer(container);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () ->
                        augmentationService.augmentContainer(containerBytes, getContainer(containerBytes), "originalContainer")
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures with LT or LTA profile", caughtException.getMessage());
    }

    @Test
    void containerWithOneLtAndOneBSignature_WrappedIntoAsics() throws IOException {
        Container container = createSignedContainer(SignatureProfile.LT);
        SignatureBuilder builder = SignatureBuilder
                .aSignature(container)
                .withSignatureProfile(SignatureProfile.B_BES);
        org.digidoc4j.Signature signature = builder.withSignatureToken(pkcs12Esteid2018SignatureToken).invokeSigning();
        container.addSignature(signature);
        byte[] containerBytes = getBytesFromContainer(container);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), "originalContainer.asice");

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(1, augmentedContainer.getTimestamps().size());
        Timestamp timestamp = augmentedContainer.getTimestamps().get(0);
        assertEquals(AsicSContainerTimestamp.class, timestamp.getClass());
        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals("originalContainer.asice", dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(containerBytes, dataFile.getBytes(), "Datafile in ASiC-S must be exactly the same as the original container.");
    }

    @Test
    void containerWithOneLtAndOneLtTmSignature_WrappedIntoAsics() throws URISyntaxException, IOException {
        byte[] containerBytes = getFile(VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(1, augmentedContainer.getTimestamps().size());
        Timestamp timestamp = augmentedContainer.getTimestamps().get(0);
        assertEquals(AsicSContainerTimestamp.class, timestamp.getClass());
        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals(VALID_BDOC_WITH_LT_TM_AND_LT_SIGNATURES, dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(containerBytes, dataFile.getBytes(), "Datafile in ASiC-S must be exactly the same as the original container.");
    }

    @Test
    void containerWithInvalidSignature_WrappedIntoAsics() throws URISyntaxException, IOException {
        byte[] containerBytes = getFile(INVALID_ASICE_WITH_EXPIRED_SIGNER_AND_OCSP);

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), INVALID_ASICE_WITH_EXPIRED_SIGNER_AND_OCSP);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(1, augmentedContainer.getTimestamps().size());
        Timestamp timestamp = augmentedContainer.getTimestamps().get(0);
        assertEquals(AsicSContainerTimestamp.class, timestamp.getClass());
        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals(INVALID_ASICE_WITH_EXPIRED_SIGNER_AND_OCSP, dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(containerBytes, dataFile.getBytes(), "The original container included in the resulting ASiC-S container must not be modified.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"B_EPES", "T"})
    void containerWithNotAugmentableSignatureProfile_Fails(String signatureProfile) throws URISyntaxException, IOException {
        String containerFilename = containersWithSingleNotAugmentableSignature.get(signatureProfile);
        byte[] containerBytes = getFile(containerFilename);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () ->
                        augmentationService.augmentContainer(containerBytes, getContainer(containerBytes), containerFilename)
        );

        assertEquals("Unable to augment. Container does not contain any Estonian signatures with LT or LTA profile", caughtException.getMessage());
    }

    @Test
    void containerWithOnlyESeal_Fails() throws URISyntaxException, IOException {
        byte[] containerBytes = getFile(ESEAL_WITH_EXPIRED_OCSP);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () ->
                        augmentationService.augmentContainer(containerBytes, getContainer(containerBytes), ESEAL_WITH_EXPIRED_OCSP)
        );

        assertEquals("Unable to augment. Container contains only e-seals, but no Estonian personal signatures", caughtException.getMessage());
    }

    @Test
    void containerWithSignatureAndESeal_OnlySignatureIsAugmented() throws URISyntaxException, IOException {
        byte[] containerBytes = getFile("LT_sig_and_LT_seal.asice");

        Container augmentedContainer = augmentationService.augmentContainer(
                containerBytes, getContainer(containerBytes), "LT_sig_and_LT_seal.asice");

        assertEquals(AsicEContainer.class, augmentedContainer.getClass());
        assertEquals(2, augmentedContainer.getSignatures().size());
        assertEquals(SignatureProfile.LTA, augmentedContainer.getSignatures().get(0).getProfile());
        List<TimestampToken> archiveTimestamps = getSignatureArchiveTimestamps(augmentedContainer, 0);
        assertEquals(1, archiveTimestamps.size(), "The signature must contain 1 archive timestamp");
        assertEquals(SignatureProfile.LT, augmentedContainer.getSignatures().get(1).getProfile());
    }

    private List<TimestampToken> getSignatureArchiveTimestamps(Container container, int signatureIndex) {
        return ((AsicSignature) container.getSignatures().get(signatureIndex)).getOrigin().getDssSignature().getArchiveTimestamps();
    }
}
