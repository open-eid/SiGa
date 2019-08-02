package ee.openeid.siga.client.hashcode;

import eu.europa.esig.dss.InMemoryDocument;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.impl.asic.manifest.AsicManifest;
import org.digidoc4j.impl.asic.manifest.ManifestEntry;
import org.digidoc4j.impl.asic.manifest.ManifestParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ee.openeid.siga.client.hashcode.HashcodeContainerWriter.SIGNATURE_FILE_PREFIX;
import static ee.openeid.siga.client.hashcode.HashcodesDataFileCreator.convertToHashcodeEntries;
import static ee.openeid.siga.client.hashcode.HashcodesDataFileCreator.createHashcodeDataFile;


public class HashcodeContainer {

    private final List<byte[]> signatures = new ArrayList<>();
    private final List<HashcodeDataFile> hashcodeDataFiles = new ArrayList<>();
    @Getter
    private Map<String, byte[]> regularDataFiles = new HashMap<>();
    private Map<String, ManifestEntry> manifest;

    @Builder(builderClassName = "FromRegularContainerBuilder", builderMethodName = "fromRegularContainerBuilder")
    @SneakyThrows
    public HashcodeContainer(InputStream containerInputStream) {
        processContainer(containerInputStream);
    }

    @Builder(builderClassName = "FromHashcodeContainerBuilder", builderMethodName = "fromHashcodeContainerBuilder")
    @SneakyThrows
    public HashcodeContainer(String base64Container, Map<String, byte[]> regularDataFiles) {
        InputStream containerInputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64Container));
        processContainer(containerInputStream);
        this.regularDataFiles = regularDataFiles;
    }

    private void processContainer(InputStream regularContainerInputStream) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(regularContainerInputStream);
        ZipEntry entry;
        while ((entry = zipStream.getNextEntry()) != null) {
            operateWithEntry(entry, zipStream);
        }
        zipStream.close();
        addMimeTypes();
    }

    public byte[] getRegularContainer() {
        validateDataFileHashes(regularDataFiles);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HashcodeContainerWriter cw = new HashcodeContainerWriter(outputStream);
        cw.writeMimeType();
        cw.writeManifest(hashcodeDataFiles);
        cw.writeRegularDataFiles(regularDataFiles);
        cw.writeSignatures(signatures);
        cw.finalizeZipFile();
        return outputStream.toByteArray();
    }

    private void validateDataFileHashes(Map<String, byte[]> regularDataFiles) {
        hashcodeDataFiles.stream()
                .filter(hc -> !regularDataFiles.containsKey(hc.getFileName()))
                .findFirst().ifPresent(hc -> {
            throw new RuntimeException("Cannot create regular container. File not found in hashcode container: " + hc.getFileName());
        });

        hashcodeDataFiles.stream()
                .filter(hc -> validateFileHash(regularDataFiles, hc)).findFirst()
                .ifPresent(hc -> {
                    throw new RuntimeException("Cannot create regular container. File hash does not match file hash in hashcode container: " + hc.getFileName());
                });
    }

    @SneakyThrows
    private boolean validateFileHash(Map<String, byte[]> regularDataFiles, HashcodeDataFile hc) {
        byte[] dataFile = regularDataFiles.get(hc.getFileName());
        MessageDigest digest256 = MessageDigest.getInstance("SHA-256");
        String hash256 = Base64.getEncoder().encodeToString(digest256.digest(dataFile));
        return !hc.getFileHashSha256().equals(hash256);
    }

    public byte[] getHashcodeContainer() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HashcodeContainerWriter cw = new HashcodeContainerWriter(outputStream);
        cw.writeMimeType();
        cw.writeManifest(hashcodeDataFiles);
        cw.writeHashcodeFiles(hashcodeDataFiles);
        cw.writeSignatures(signatures);
        cw.finalizeZipFile();
        return outputStream.toByteArray();
    }

    public String getBase64HashcodeContainer() {
        return Base64.getEncoder().encodeToString(getHashcodeContainer());
    }

    private void operateWithEntry(ZipEntry entry, ZipInputStream zipStream) throws IOException {
        String entryName = entry.getName();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] byteBuff = new byte[4096];
            int bytesRead;
            while ((bytesRead = zipStream.read(byteBuff)) != -1) {
                baos.write(byteBuff, 0, bytesRead);
            }
            if (isSignatureEntry(entryName)) {
                signatures.add(baos.toByteArray());
            } else if (isDataFileEntry(entryName)) {
                byte[] dataFile = baos.toByteArray();
                regularDataFiles.put(entryName, dataFile);
                hashcodeDataFiles.add(createHashcodeDataFile(entry, dataFile));
            } else if (isManifestEntry(entryName)) {
                InMemoryDocument manifestFile = new InMemoryDocument(baos.toByteArray());
                ManifestParser manifestParser = new ManifestParser(manifestFile);
                manifest = manifestParser.getManifestFileItems();
            } else if (isHashcodeDatafileEntry(entryName)) {
                addDataFilesFromHashcodeEntries(convertToHashcodeEntries(baos.toByteArray()), entryName);
            }
            zipStream.closeEntry();
        }
    }

    private boolean isMimeType(String entryName) {
        return StringUtils.equalsIgnoreCase("mimetype", entryName);
    }

    private boolean isDataFileEntry(String entryName) {
        return !entryName.startsWith("META-INF/") && !isMimeType(entryName);
    }

    private void addMimeTypes() {
        if (manifest == null) {
            return;
        }
        hashcodeDataFiles.forEach(dataFile ->
                manifest.forEach((s, manifestEntry) -> {
                    if (dataFile.getFileName().equals(manifestEntry.getFileName())) {
                        dataFile.setMimeType(manifestEntry.getMimeType());
                    }
                }));
    }

    private boolean isSignatureEntry(String entryName) {
        return entryName.startsWith(SIGNATURE_FILE_PREFIX);
    }

    private boolean isManifestEntry(String entryName) {
        return AsicManifest.XML_PATH.equals(entryName);
    }

    private boolean isHashcodeDatafileEntry(String entryName) {
        return entryName.startsWith(HashcodesDataFilesWriter.HASHCODES_PREFIX);
    }

    private void addDataFilesFromHashcodeEntries(Map<String, HashcodesEntry> entries, String entryName) {
        entries.forEach((file, hashcodesEntry) -> {
            Optional<HashcodeDataFile> existingDataFile = hashcodeDataFiles.stream().filter(dataFile -> dataFile.getFileName().equals(file)).findAny();
            if (existingDataFile.isPresent()) {
                if (HashcodesDataFilesWriter.HASHCODES_SHA256.equals(entryName)) {
                    existingDataFile.get().setFileHashSha256(hashcodesEntry.getHash());
                } else if (HashcodesDataFilesWriter.HASHCODES_SHA512.equals(entryName)) {
                    existingDataFile.get().setFileHashSha512(hashcodesEntry.getHash());
                }
            } else {
                HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
                hashcodeDataFile.setFileName(file);
                hashcodeDataFile.setFileSize(hashcodesEntry.getSize());
                if (HashcodesDataFilesWriter.HASHCODES_SHA256.equals(entryName)) {
                    hashcodeDataFile.setFileHashSha256(hashcodesEntry.getHash());
                } else if (HashcodesDataFilesWriter.HASHCODES_SHA512.equals(entryName)) {
                    hashcodeDataFile.setFileHashSha512(hashcodesEntry.getHash());
                }
                hashcodeDataFiles.add(hashcodeDataFile);
            }
        });
    }
}
