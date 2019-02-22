package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.common.exception.TechnicalException;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DigestDocument;
import eu.europa.esig.dss.MimeType;
import org.digidoc4j.Configuration;
import org.digidoc4j.DetachedXadesSignatureBuilder;
import org.digidoc4j.Signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ee.openeid.siga.service.signature.hashcode.HashCodeContainerCreator.SIGNATURE_FILE_PREFIX;

public class HashCodeContainer {

    private List<HashCodeDataFile> dataFiles = new ArrayList<>();
    private List<Signature> signatures = new ArrayList<>();

    public void save(OutputStream outputStream) {
        createHashCodeContainer(outputStream);
    }

    public void open(InputStream inputStream) {
        try {
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                operateWithEntry(entry, zipStream);
            }
            zipStream.close();
        } catch (IOException e) {
            throw new TechnicalException("Unable to open hashcode container");
        }
    }

    private void createHashCodeContainer(OutputStream outputStream) {
        HashCodeContainerCreator hashCodeContainerCreator = new HashCodeContainerCreator(outputStream);
        hashCodeContainerCreator.writeMimeType();
        hashCodeContainerCreator.writeManifest(convertDataFiles());
        hashCodeContainerCreator.writeHashCodeFiles(dataFiles);
        hashCodeContainerCreator.writeSignatures(signatures);
        hashCodeContainerCreator.finalizeZipFile();
    }

    private void operateWithEntry(ZipEntry entry, ZipInputStream zipStream) throws IOException {
        String entryName = entry.getName();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] byteBuff = new byte[4096];
            int bytesRead;
            while ((bytesRead = zipStream.read(byteBuff)) != -1) {
                out.write(byteBuff, 0, bytesRead);
            }
            if (entryName.startsWith(SIGNATURE_FILE_PREFIX)) {
                DetachedXadesSignatureBuilder signatureBuilder = DetachedXadesSignatureBuilder.withConfiguration(new Configuration());
                signatures.add(signatureBuilder.openAdESSignature(out.toByteArray()));
            } else if (entryName.startsWith(HashCodesDataFile.HASHCODES_PREFIX)) {
                HashCodesDataFileParser parser = new HashCodesDataFileParser(out.toByteArray());
                addDataFileEntries(parser.getEntries(), entryName);
            }

            zipStream.closeEntry();
        }
    }

    private void addDataFileEntries(Map<String, HashCodesEntry> entries, String entryName) {
        entries.forEach((file, hashCodesEntry) -> {
            Optional<HashCodeDataFile> existingDataFile = dataFiles.stream().filter(dataFile -> dataFile.getFileName().equals(file)).findAny();
            if (existingDataFile.isPresent()) {
                if (HashCodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    existingDataFile.get().setFileHashSha256(hashCodesEntry.getHash());
                } else if (HashCodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    existingDataFile.get().setFileHashSha512(hashCodesEntry.getHash());
                }
            } else {
                HashCodeDataFile hashCodeDataFile = new HashCodeDataFile();
                hashCodeDataFile.setFileName(file);
                hashCodeDataFile.setFileSize(hashCodesEntry.getSize());
                if (HashCodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    hashCodeDataFile.setFileHashSha256(hashCodesEntry.getHash());
                } else if (HashCodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    hashCodeDataFile.setFileHashSha512(hashCodesEntry.getHash());
                }
                dataFiles.add(hashCodeDataFile);
            }
        });
    }

    private List<org.digidoc4j.DataFile> convertDataFiles() {
        return dataFiles.stream().map(d -> {
            DSSDocument dssDocument = new DigestDocument();
            dssDocument.setMimeType(MimeType.BINARY);
            dssDocument.setName(d.getFileName());
            org.digidoc4j.DataFile dataFile = new org.digidoc4j.DataFile();
            dataFile.setDocument(dssDocument);
            return dataFile;

        }).collect(Collectors.toList());
    }

    public List<Signature> getSignatures() {
        return signatures;
    }

    public List<HashCodeDataFile> getDataFiles() {
        return dataFiles;
    }

    public void addSignature(Signature signature) {
        signatures.add(signature);
    }

    public void addDataFile(HashCodeDataFile dataFile) {
        if (signatures.size() > 0)
            throw new SignatureExistsException("Unable to add data file when signature exists");
        dataFiles.add(dataFile);
    }
}
