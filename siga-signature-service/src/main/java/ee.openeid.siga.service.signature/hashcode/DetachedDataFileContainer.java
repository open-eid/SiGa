package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.HashcodeDataFile;
import ee.openeid.siga.common.SignatureWrapper;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.service.signature.session.SessionIdGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import eu.europa.esig.dss.DSSDocument;
import eu.europa.esig.dss.DigestDocument;
import eu.europa.esig.dss.MimeType;
import org.apache.commons.lang3.StringUtils;

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

import static ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainerCreator.SIGNATURE_FILE_PREFIX;

public class DetachedDataFileContainer {

    private List<HashcodeDataFile> dataFiles = new ArrayList<>();
    private List<SignatureWrapper> signatures = new ArrayList<>();

    public void save(OutputStream outputStream) {
        createHashcodeContainer(outputStream);
    }

    public void open(InputStream inputStream) {
        try {
            ZipInputStream zipStream = new ZipInputStream(inputStream);
            ZipEntry entry;
            boolean isValidZip = false;
            while ((entry = zipStream.getNextEntry()) != null) {
                operateWithEntry(entry, zipStream);
                isValidZip = true;
            }
            zipStream.close();
            if (!isValidZip) {
                throw new InvalidContainerException("Invalid hashcode container");
            }
            validateDataFiles();
        } catch (IOException e) {
            throw new InvalidContainerException("Unable to open hashcode container");
        }
    }

    public List<SignatureWrapper> getSignatures() {
        return signatures;
    }

    public List<HashcodeDataFile> getDataFiles() {
        return dataFiles;
    }

    public void addSignature(SignatureWrapper signature) {
        signatures.add(signature);
    }

    public void addDataFile(HashcodeDataFile dataFile) {
        if (signatures.size() > 0)
            throw new SignatureExistsException("Unable to add data file when signature exists");
        dataFiles.add(dataFile);
    }

    private void validateDataFiles() {
        if (dataFiles.isEmpty()) {
            throw new InvalidContainerException("Container must have data files");
        }
        dataFiles.forEach(dataFile -> {
            if (StringUtils.isBlank(dataFile.getFileHashSha256())) {
                throw new InvalidContainerException("Hashcode container is missing SHA256 hash");
            }
            if (StringUtils.isBlank(dataFile.getFileHashSha512())) {
                throw new InvalidContainerException("Hashcode container is missing SHA512 hash");
            }
            if (!isValidFileName(dataFile.getFileName())) {
                throw new InvalidContainerException("Hashcode container contains invalid file name");
            }
        });
    }

    private boolean isValidFileName(String fileName) {
        if (fileName.contains("/")) {
            return false;
        }
        return !fileName.contains("\\");
    }

    private void createHashcodeContainer(OutputStream outputStream) {
        DetachedDataFileContainerCreator hashcodeContainerCreator = new DetachedDataFileContainerCreator(outputStream);
        hashcodeContainerCreator.writeMimeType();
        hashcodeContainerCreator.writeManifest(convertDataFiles());
        hashcodeContainerCreator.writeHashcodeFiles(dataFiles);
        hashcodeContainerCreator.writeSignatures(signatures);

        hashcodeContainerCreator.finalizeZipFile();
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
                signatures.add(createSignatureWrapper(out.toByteArray()));
            } else if (entryName.startsWith(HashcodesDataFile.HASHCODES_PREFIX)) {
                HashcodesDataFileParser parser = new HashcodesDataFileParser(out.toByteArray());
                addDataFileEntries(parser.getEntries(), entryName);
            }
            zipStream.closeEntry();
        }
    }

    private SignatureWrapper createSignatureWrapper(byte[] signature) {

        SignatureDataFilesParser parser = new SignatureDataFilesParser(signature);
        Map<String, String> dataFiles = parser.getEntries();

        SignatureWrapper signatureWrapper = new SignatureWrapper();
        signatureWrapper.setGeneratedSignatureId(SessionIdGenerator.generateSessionId());
        signatureWrapper.setSignature(signature);
        ContainerUtil.addSignatureDataFilesEntries(signatureWrapper, dataFiles);
        return signatureWrapper;
    }

    private void addDataFileEntries(Map<String, HashcodesEntry> entries, String entryName) {
        entries.forEach((file, hashcodesEntry) -> {
            Optional<HashcodeDataFile> existingDataFile = dataFiles.stream().filter(dataFile -> dataFile.getFileName().equals(file)).findAny();
            if (existingDataFile.isPresent()) {
                if (HashcodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    existingDataFile.get().setFileHashSha256(hashcodesEntry.getHash());
                } else if (HashcodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    existingDataFile.get().setFileHashSha512(hashcodesEntry.getHash());
                }
            } else {
                HashcodeDataFile hashcodeDataFile = new HashcodeDataFile();
                hashcodeDataFile.setFileName(file);
                hashcodeDataFile.setFileSize(hashcodesEntry.getSize());
                if (HashcodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    hashcodeDataFile.setFileHashSha256(hashcodesEntry.getHash());
                } else if (HashcodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    hashcodeDataFile.setFileHashSha512(hashcodesEntry.getHash());
                }
                dataFiles.add(hashcodeDataFile);
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

}
