package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import eu.europa.esig.dss.model.MimeType;
import org.digidoc4j.Container;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.impl.asic.manifest.AsicManifest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class HashcodeContainerCreator {

    static final String SIGNATURE_FILE_PREFIX = "META-INF/signatures";
    static final String ZIP_ENTRY_MIMETYPE = "mimetype";
    private static final String SIGNATURE_FILE_EXTENSION = ".xml";
    private final ZipOutputStream zipOutputStream;

    HashcodeContainerCreator(OutputStream outputStream) {
        this.zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
    }

    private static ZipEntry getZipEntry(byte[] mimeTypeBytes, String name) {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setSize(mimeTypeBytes.length);
        zipEntry.setCompressedSize(mimeTypeBytes.length);
        CRC32 crc = new CRC32();
        crc.update(mimeTypeBytes);
        zipEntry.setCrc(crc.getValue());
        return zipEntry;
    }

    void writeHashcodeFiles(List<HashcodeDataFile> dataFiles) {
        writeHashcodeFile(dataFiles, DigestAlgorithm.SHA256, HashcodesDataFile.HASHCODES_SHA256);
        if (dataFiles.stream().noneMatch(df -> df.getFileHashSha512() == null)) {
            writeHashcodeFile(dataFiles, DigestAlgorithm.SHA512, HashcodesDataFile.HASHCODES_SHA512);
        }
    }

    private void writeHashcodeFile(List<HashcodeDataFile> dataFiles, DigestAlgorithm digestAlgorithm, String entryName) {
        HashcodesDataFile hashcodesDataFile = new HashcodesDataFile(digestAlgorithm);
        hashcodesDataFile.generateHashcodeFile(dataFiles);
        new EntryCallback(new ZipEntry(entryName)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                hashcodesDataFile.writeTo(stream);
            }
        }.write();
    }

    void finalizeZipFile() {
        try {
            zipOutputStream.finish();
            zipOutputStream.close();
        } catch (IOException e) {
            throw new TechnicalException("Unable to finish creating ZIP container");
        }
    }

    void writeMimeType() {
        byte[] mimeType = MimeType.ASICE.getMimeTypeString().getBytes();
        new BytesEntryCallback(getZipEntry(mimeType, ZIP_ENTRY_MIMETYPE), mimeType).write();
    }

    void writeSignatures(List<HashcodeSignatureWrapper> wrappers) {
        for (int i = 0; i < wrappers.size(); i++) {
            byte[] signatureData = wrappers.get(i).getSignature();
            String signatureName = SIGNATURE_FILE_PREFIX + i + SIGNATURE_FILE_EXTENSION;
            new BytesEntryCallback(getZipEntry(signatureData, signatureName), signatureData).write();
        }
    }

    void writeManifest(List<org.digidoc4j.DataFile> dataFiles) {
        final AsicManifest asicManifest = new AsicManifest(Container.DocumentType.ASICE.name());
        asicManifest.addFileEntry(dataFiles);
        new EntryCallback(new ZipEntry(AsicManifest.XML_PATH)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                asicManifest.writeTo(stream);
            }
        }.write();
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

        void write() {

            try {
                zipOutputStream.putNextEntry(entry);
                doWithEntryStream(zipOutputStream);
                zipOutputStream.closeEntry();
            } catch (IOException e) {
                throw new TechnicalException("Unable to write Zip entry");
            }
        }

        abstract void doWithEntryStream(OutputStream stream) throws IOException;
    }

}
