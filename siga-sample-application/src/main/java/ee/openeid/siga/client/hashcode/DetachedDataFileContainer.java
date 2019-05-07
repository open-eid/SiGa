package ee.openeid.siga.client.hashcode;

import lombok.Builder;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static ee.openeid.siga.client.hashcode.DetachedDataFileContainerWriter.SIGNATURE_FILE_PREFIX;
import static ee.openeid.siga.client.hashcode.HashcodesDataFileCreator.convertToHashcodeEntries;
import static ee.openeid.siga.client.hashcode.HashcodesDataFileCreator.createHashcodeDataFile;

public class DetachedDataFileContainer {

    private List<HashcodeDataFile> hashcodeDataFiles = new ArrayList<>();
    private List<byte[]> signatures = new ArrayList<>();

    @Builder
    @SneakyThrows
    public DetachedDataFileContainer(InputStream inputStream) {
        ZipInputStream zipStream = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zipStream.getNextEntry()) != null) {
            operateWithEntry(entry, zipStream);
        }
        zipStream.close();
    }

    public byte[] getHashcodeContainer() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DetachedDataFileContainerWriter cw = new DetachedDataFileContainerWriter(outputStream);
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
                hashcodeDataFiles.add(createHashcodeDataFile(entry, baos.toByteArray()));
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

    private boolean isSignatureEntry(String entryName) {
        return entryName.startsWith(SIGNATURE_FILE_PREFIX);
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
