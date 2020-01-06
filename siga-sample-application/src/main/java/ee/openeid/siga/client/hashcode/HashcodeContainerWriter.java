package ee.openeid.siga.client.hashcode;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.MimeType;
import lombok.SneakyThrows;
import org.digidoc4j.Container;
import org.digidoc4j.DataFile;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.impl.asic.manifest.AsicManifest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class HashcodeContainerWriter {

    public static final String SIGNATURE_FILE_PREFIX = "META-INF/signatures";
    private static final String ZIP_ENTRY_MIMETYPE = "mimetype";
    private static final String SIGNATURE_FILE_EXTENSION = ".xml";
    private final ZipOutputStream zipOutputStream;

    public HashcodeContainerWriter(OutputStream outputStream) {
        this.zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
    }

    private static ZipEntry createZipEntry(byte[] mimeTypeBytes, String name) {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setSize(mimeTypeBytes.length);
        zipEntry.setCompressedSize(mimeTypeBytes.length);
        CRC32 crc = new CRC32();
        crc.update(mimeTypeBytes);
        zipEntry.setCrc(crc.getValue());
        return zipEntry;
    }

    public void writeHashcodeFiles(List<HashcodeDataFile> dataFiles) {
        writeHashcodeFile(dataFiles, DigestAlgorithm.SHA256, HashcodesDataFilesWriter.HASHCODES_SHA256);
        writeHashcodeFile(dataFiles, DigestAlgorithm.SHA512, HashcodesDataFilesWriter.HASHCODES_SHA512);
    }

    private void writeHashcodeFile(List<HashcodeDataFile> dataFiles, DigestAlgorithm digestAlgorithm, String entryName) {
        HashcodesDataFilesWriter hashcodesDataFilesWriter = new HashcodesDataFilesWriter(digestAlgorithm);
        hashcodesDataFilesWriter.generateHashcodeFile(dataFiles);
        new EntryCallback(new ZipEntry(entryName)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                hashcodesDataFilesWriter.writeTo(stream);
            }
        }.write();
    }

    @SneakyThrows
    public void finalizeZipFile() {
        zipOutputStream.finish();
        zipOutputStream.close();
    }

    public void writeMimeType() {
        byte[] mimeType = MimeType.ASICE.getMimeTypeString().getBytes();
        new BytesEntryCallback(createZipEntry(mimeType, ZIP_ENTRY_MIMETYPE), mimeType).write();
    }

    public void writeRegularDataFiles(Map<String, byte[]> regularDataFiles) {
        regularDataFiles.forEach((fileName, fileContent) -> new BytesEntryCallback(createZipEntry(fileContent, fileName), fileContent).write());
    }

    public void writeSignatures(List<byte[]> wrappers) {
        for (int i = 0; i < wrappers.size(); i++) {
            byte[] signatureData = wrappers.get(i);
            String signatureName = SIGNATURE_FILE_PREFIX + i + SIGNATURE_FILE_EXTENSION;
            new BytesEntryCallback(createZipEntry(signatureData, signatureName), signatureData).write();
        }
    }

    public void writeManifest(List<HashcodeDataFile> hashcodeDataFiles) {
        List<DataFile> dataFiles = convertDataFiles(hashcodeDataFiles);
        final AsicManifest asicManifest = new AsicManifest(Container.DocumentType.ASICE.name());
        asicManifest.addFileEntry(dataFiles);
        new EntryCallback(new ZipEntry(AsicManifest.XML_PATH)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                asicManifest.writeTo(stream);
            }
        }.write();
    }

    private List<DataFile> convertDataFiles(List<HashcodeDataFile> hashcodeDataFiles) {
        return hashcodeDataFiles.stream().map(d -> {
            DSSDocument dssDocument = new DigestDocument();
            dssDocument.setMimeType(MimeType.fromMimeTypeString(d.getMimeType()));
            dssDocument.setName(d.getFileName());
            DataFile dataFile = new DataFile();
            dataFile.setDocument(dssDocument);
            return dataFile;
        }).collect(Collectors.toList());
    }

    private class BytesEntryCallback extends EntryCallback {

        private final byte[] data;

        BytesEntryCallback(ZipEntry entry, byte[] data) {
            super(entry);
            this.data = data;
        }

        @Override
        void doWithEntryStream(OutputStream stream) throws IOException {
            stream.write(data);
        }
    }

    private abstract class EntryCallback {

        private final ZipEntry entry;

        EntryCallback(ZipEntry entry) {
            this.entry = entry;
        }

        @SneakyThrows
        void write() {
            zipOutputStream.putNextEntry(entry);
            doWithEntryStream(zipOutputStream);
            zipOutputStream.closeEntry();
        }

        abstract void doWithEntryStream(OutputStream stream) throws IOException;
    }
}
