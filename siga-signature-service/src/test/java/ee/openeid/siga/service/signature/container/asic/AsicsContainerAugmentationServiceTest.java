package ee.openeid.siga.service.signature.container.asic;

import ee.openeid.siga.common.event.SigaEventLogger;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.service.signature.test.TestUtil;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.Container.DocumentType;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DataFile;
import org.digidoc4j.Timestamp;
import org.digidoc4j.TimestampBuilder;
import org.digidoc4j.impl.asic.asics.AsicSCompositeContainer;
import org.digidoc4j.impl.asic.asics.AsicSContainer;
import org.digidoc4j.impl.asic.asics.AsicSContainerTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static ee.openeid.siga.service.signature.test.RequestUtil.INVALID_DDOC_WITH_EXPIRED_SIGNER_AND_OCSP;
import static ee.openeid.siga.service.signature.test.RequestUtil.VALID_ASICS_WITH_INCORRECT_MIMETYPE_IN_MANIFEST_XML;
import static ee.openeid.siga.service.signature.test.TestUtil.getContainer;
import static ee.openeid.siga.service.signature.test.TestUtil.getFile;
import static org.digidoc4j.utils.ContainerUtils.DDOC_MIMETYPE_STRING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@ExtendWith(MockitoExtension.class)
class AsicsContainerAugmentationServiceTest {
    @InjectMocks
    private AsicsContainerAugmentationService augmentationService;
    @Spy
    private Configuration configuration = Configuration.of(Configuration.Mode.TEST);
    @Mock
    private SigaEventLogger eventLogger;

    @Test
    void containerWithoutTokensAndSignatures_Fails() {
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(container)
        );

        assertEquals("Unable to augment. Container does not contain any timestamp tokens.", caughtException.getMessage());
    }

    @Test
    void containerWithNotParseableInnerContainer_Fails() {
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        Timestamp timestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(timestamp);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(container)
        );

        assertEquals("Unable to augment. The datafile in ASiC-S container must be a valid container.",
                caughtException.getMessage());
    }

    @Test
    void containerWithInnerAsics_Fails() throws IOException {
        Container innerContainer = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] innerContainerBytes = TestUtil.getBytesFromContainer(innerContainer);
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile(innerContainerBytes, "innerContainer.asics", MimeTypeEnum.ASICS.getMimeTypeString()))
                .build();
        Timestamp timestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(timestamp);

        InvalidSessionDataException caughtException = assertThrows(
                InvalidSessionDataException.class, () -> augmentationService.augmentContainer(container)
        );

        assertEquals("Unable to augment. Invalid container type (ASICS) found inside ASiC-S container. Allowed inner types are: ASICE, BDOC, DDOC",
                caughtException.getMessage());
    }

    @Test
    void containerWithInnerDdocAndSingleTimestamp_DdocMimetypeAddedToArchiveManifest() throws IOException, URISyntaxException {
        byte[] innerContainerBytes = getFile(INVALID_DDOC_WITH_EXPIRED_SIGNER_AND_OCSP);
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile(innerContainerBytes, INVALID_DDOC_WITH_EXPIRED_SIGNER_AND_OCSP, DDOC_MIMETYPE_STRING))
                .build();
        Timestamp originalTimestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(originalTimestamp);

        Container augmentedContainer = augmentationService.augmentContainer(container);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(2, augmentedContainer.getTimestamps().size());

        AsicSContainerTimestamp timestamp1 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(0);
        assertEquals(originalTimestamp.getCreationTime(), timestamp1.getCreationTime());
        assertEquals(originalTimestamp.getCertificate().getX509Certificate(), timestamp1.getCertificate().getX509Certificate());
        assertNull(timestamp1.getArchiveManifest());

        AsicSContainerTimestamp timestamp2 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(1);
        assertThat(timestamp1.getCreationTime(), lessThanOrEqualTo(timestamp2.getCreationTime()));
        String timestamp2Xml = IOUtils.toString(timestamp2.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp2Xml.contains("<asic:DataObjectReference MimeType=\"application/x-ddoc\" URI=\"container.ddoc\">"));

        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals(INVALID_DDOC_WITH_EXPIRED_SIGNER_AND_OCSP, dataFile.getName());
        assertEquals(DDOC_MIMETYPE_STRING, dataFile.getMediaType());
        assertArrayEquals(innerContainerBytes, dataFile.getBytes());
    }

    @Test
    void containerWithInnerBdocAndSingleTimestamp_BdocMimetypeAddedToArchiveManifest() throws IOException {
        Container innerContainer = ContainerBuilder.aContainer(DocumentType.BDOC)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] innerContainerBytes = TestUtil.getBytesFromContainer(innerContainer);
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile(innerContainerBytes, "test.bdoc", MimeTypeEnum.ASICE.getMimeTypeString()))
                .build();
        Timestamp originalTimestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(originalTimestamp);

        Container augmentedContainer = augmentationService.augmentContainer(container);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(2, augmentedContainer.getTimestamps().size());

        AsicSContainerTimestamp timestamp1 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(0);
        assertEquals(originalTimestamp.getCreationTime(), timestamp1.getCreationTime());
        assertEquals(originalTimestamp.getCertificate().getX509Certificate(), timestamp1.getCertificate().getX509Certificate());
        assertNull(timestamp1.getArchiveManifest());

        AsicSContainerTimestamp timestamp2 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(1);
        assertThat(timestamp1.getCreationTime(), lessThanOrEqualTo(timestamp2.getCreationTime()));
        String timestamp2Xml = IOUtils.toString(timestamp2.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp2Xml.contains("<asic:DataObjectReference MimeType=\"application/vnd.etsi.asic-e+zip\" URI=\"test.bdoc\">"));

        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals("test.bdoc", dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(innerContainerBytes, dataFile.getBytes());
    }

    @Test
    void containerWithInnerAsiceAndSingleTimestamp_AsiceMimetypeAddedToArchiveManifest() throws IOException {
        Container innerContainer = ContainerBuilder.aContainer(DocumentType.ASICE)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] innerContainerBytes = TestUtil.getBytesFromContainer(innerContainer);
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile(innerContainerBytes, "test.asice", MimeTypeEnum.ASICE.getMimeTypeString()))
                .build();
        Timestamp originalTimestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(originalTimestamp);

        Container augmentedContainer = augmentationService.augmentContainer(container);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(2, augmentedContainer.getTimestamps().size());

        AsicSContainerTimestamp timestamp1 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(0);
        assertEquals(originalTimestamp.getCreationTime(), timestamp1.getCreationTime());
        assertEquals(originalTimestamp.getCertificate().getX509Certificate(), timestamp1.getCertificate().getX509Certificate());
        assertNull(timestamp1.getArchiveManifest());

        AsicSContainerTimestamp timestamp2 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(1);
        assertThat(timestamp1.getCreationTime(), lessThanOrEqualTo(timestamp2.getCreationTime()));
        String timestamp2Xml = IOUtils.toString(timestamp2.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp2Xml.contains("<asic:DataObjectReference MimeType=\"application/vnd.etsi.asic-e+zip\" URI=\"test.asice\">"));

        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals("test.asice", dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(innerContainerBytes, dataFile.getBytes());
    }

    @Test
    void containerWithInnerAsiceAugmentedTwice_TwoArchiveManifestsAdded() throws IOException {
        Container innerContainer = ContainerBuilder.aContainer(DocumentType.ASICE)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile("test-content".getBytes(), "test.txt", "text/plain"))
                .build();
        byte[] innerContainerBytes = TestUtil.getBytesFromContainer(innerContainer);
        Container container = ContainerBuilder.aContainer(DocumentType.ASICS)
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new DataFile(innerContainerBytes, "test.asice", MimeTypeEnum.ASICE.getMimeTypeString()))
                .build();
        Timestamp originalTimestamp = TimestampBuilder.aTimestamp(container)
                .invokeTimestamping();
        container.addTimestamp(originalTimestamp);
        Container firstAugmentedContainer = augmentationService.augmentContainer(container);

        Container augmentedContainer = augmentationService.augmentContainer(firstAugmentedContainer);

        assertEquals(AsicSContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(3, augmentedContainer.getTimestamps().size());

        AsicSContainerTimestamp timestamp1 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(0);
        assertEquals(originalTimestamp.getCreationTime(), timestamp1.getCreationTime());
        assertEquals(originalTimestamp.getCertificate().getX509Certificate(), timestamp1.getCertificate().getX509Certificate());
        assertNull(timestamp1.getArchiveManifest());

        AsicSContainerTimestamp timestamp2 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(1);
        assertThat(timestamp1.getCreationTime(), lessThanOrEqualTo(timestamp2.getCreationTime()));
        String timestamp2Xml = IOUtils.toString(timestamp2.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp2Xml.contains("<asic:DataObjectReference MimeType=\"application/vnd.etsi.asic-e+zip\" URI=\"test.asice\">"));

        AsicSContainerTimestamp timestamp3 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(2);
        assertThat(timestamp2.getCreationTime(), lessThanOrEqualTo(timestamp3.getCreationTime()));
        String timestamp3Xml = IOUtils.toString(timestamp3.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp3Xml.contains("<asic:DataObjectReference MimeType=\"application/vnd.etsi.asic-e+zip\" URI=\"test.asice\">"));

        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals("test.asice", dataFile.getName());
        assertEquals(MimeTypeEnum.ASICE.getMimeTypeString(), dataFile.getMediaType());
        assertArrayEquals(innerContainerBytes, dataFile.getBytes());
    }

    @Test
    void containerWithInvalidMimeTypeDefinedInManifestXml_CorrectMimetypeAddedToArchiveManifest() throws IOException, URISyntaxException {
        // This ASiC-S container contains a DDOC file, but the mimetype of this DDOC is specified as "application/vnd.etsi.asic-e+zip"
        Container container = getContainer(getFile(VALID_ASICS_WITH_INCORRECT_MIMETYPE_IN_MANIFEST_XML));

        Container augmentedContainer = augmentationService.augmentContainer(container);

        assertEquals(AsicSCompositeContainer.class, augmentedContainer.getClass());
        assertEquals(1, augmentedContainer.getDataFiles().size());
        assertEquals(0, augmentedContainer.getSignatures().size());
        assertEquals(2, augmentedContainer.getTimestamps().size());

        AsicSContainerTimestamp timestamp1 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(0);
        assertNull(timestamp1.getArchiveManifest());

        AsicSContainerTimestamp timestamp2 = (AsicSContainerTimestamp) augmentedContainer.getTimestamps().get(1);
        assertThat(timestamp1.getCreationTime(), lessThanOrEqualTo(timestamp2.getCreationTime()));
        String timestamp2Xml = IOUtils.toString(timestamp2.getArchiveManifest().getManifestDocument().openStream(), StandardCharsets.UTF_8);
        assertTrue(timestamp2Xml.contains("<asic:DataObjectReference MimeType=\"application/x-ddoc\" URI=\"container.ddoc\">"));

        DataFile dataFile = augmentedContainer.getDataFiles().get(0);
        assertEquals("container.ddoc", dataFile.getName());
        assertEquals(DDOC_MIMETYPE_STRING, dataFile.getMediaType());
    }

}
