package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.common.model.HashcodeDataFile;
import ee.openeid.siga.common.model.HashcodeSignatureWrapper;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.service.signature.util.ContainerUtil;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.MimeType;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.impl.asic.manifest.AsicManifest;
import org.digidoc4j.impl.asic.manifest.ManifestEntry;
import org.digidoc4j.impl.asic.manifest.ManifestParser;

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

import static ee.openeid.siga.service.signature.hashcode.HashcodeContainerCreator.SIGNATURE_FILE_PREFIX;

public class HashcodeContainer {
    private static final int MAX_FILE_SIZE = 500000;
    private List<HashcodeDataFile> dataFiles = new ArrayList<>();
    private List<HashcodeSignatureWrapper> signatures = new ArrayList<>();
    private Map<String, ManifestEntry> manifest;
    private ServiceType serviceType;

    public HashcodeContainer(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public HashcodeContainer() {
        this.serviceType = ServiceType.REST;
    }

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
            addMimeTypes();
        } catch (IOException e) {
            throw new InvalidContainerException("Unable to open hashcode container");
        }
    }

    public List<HashcodeSignatureWrapper> getSignatures() {
        return signatures;
    }

    public List<HashcodeDataFile> getDataFiles() {
        return dataFiles;
    }

    public void addSignature(HashcodeSignatureWrapper signature) {
        signatures.add(signature);
    }

    public void addDataFile(HashcodeDataFile dataFile) {
        if (!signatures.isEmpty())
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
            if (ServiceType.REST == serviceType && StringUtils.isBlank(dataFile.getFileHashSha512())) {
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
        HashcodeContainerCreator hashcodeContainerCreator = new HashcodeContainerCreator(outputStream);
        hashcodeContainerCreator.writeMimeType();
        hashcodeContainerCreator.writeManifest(convertDataFiles());
        hashcodeContainerCreator.writeHashcodeFiles(dataFiles);
        hashcodeContainerCreator.writeSignatures(signatures);

        hashcodeContainerCreator.finalizeZipFile();
    }

    private void operateWithEntry(ZipEntry entry, ZipInputStream zipStream) throws IOException {
        validateFileSize(entry);
        String entryName = entry.getName();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] byteBuff = new byte[4096];
            int bytesRead;
            while ((bytesRead = zipStream.read(byteBuff)) != -1) {
                out.write(byteBuff, 0, bytesRead);
            }
            if (AsicManifest.XML_PATH.equals(entryName)) {
                InMemoryDocument manifestFile = new InMemoryDocument(out.toByteArray());
                ManifestParser manifestParser = new ManifestParser(manifestFile);
                manifest = manifestParser.getManifestFileItems();
            } else if (entryName.startsWith(SIGNATURE_FILE_PREFIX)) {
                signatures.add(createSignatureWrapper(out.toByteArray()));
            } else if (entryName.startsWith(HashcodesDataFile.HASHCODES_PREFIX)) {
                HashcodesDataFileParser parser = new HashcodesDataFileParser(out.toByteArray());
                addDataFileEntries(parser.getEntries(), entryName);
            }
            zipStream.closeEntry();
        }
    }

    private HashcodeSignatureWrapper createSignatureWrapper(byte[] signature) {

        SignatureDataFilesParser parser = new SignatureDataFilesParser(signature);
        Map<String, String> dataFileEntries = parser.getEntries();

        HashcodeSignatureWrapper signatureWrapper = new HashcodeSignatureWrapper();
        signatureWrapper.setGeneratedSignatureId(UUIDGenerator.generateUUID());
        signatureWrapper.setSignature(signature);
        ContainerUtil.addSignatureDataFilesEntries(signatureWrapper, dataFileEntries);
        return signatureWrapper;
    }

    private void addMimeTypes() {
        if (manifest == null) {
            return;
        }
        dataFiles.forEach(dataFile ->
                manifest.forEach((s, manifestEntry) -> {
                    if (dataFile.getFileName().equals(manifestEntry.getFileName())) {
                        dataFile.setMimeType(manifestEntry.getMimeType());
                    }
                }));

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

    private void validateFileSize(ZipEntry zipEntry) {
        if (zipEntry.getSize() > MAX_FILE_SIZE) {
            throw new InvalidContainerException("Container contains file which is too large");
        }
    }

    private List<org.digidoc4j.DataFile> convertDataFiles() {
        return dataFiles.stream().map(d -> {
            DSSDocument dssDocument = new DigestDocument();
            dssDocument.setMimeType(d.getMimeType() != null ? MimeType.fromMimeTypeString(d.getMimeType()) : MimeType.BINARY);
            dssDocument.setName(d.getFileName());
            org.digidoc4j.DataFile dataFile = new org.digidoc4j.DataFile();
            dataFile.setDocument(dssDocument);
            return dataFile;

        }).collect(Collectors.toList());
    }

}
