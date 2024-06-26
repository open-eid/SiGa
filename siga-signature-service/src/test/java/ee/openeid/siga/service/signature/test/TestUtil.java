package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.service.signature.hashcode.HashcodesDataFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.SignatureBuilder;
import org.digidoc4j.SignatureProfile;
import org.digidoc4j.signers.PKCS12SignatureToken;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

public class TestUtil {
    public static final String MANIFEST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.etsi.asic-e+zip\"/><manifest:file-entry manifest:full-path=\"first datafile.txt\" manifest:media-type=\"application/octet-stream\"/><manifest:file-entry manifest:full-path=\"second datafile.txt\" manifest:media-type=\"application/octet-stream\"/></manifest:manifest>";
    public static final String HASHCODES_SHA256_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms\" size=\"10\"/></hashcodes>";
    public static final String HASHCODES_SHA512_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw\" size=\"10\"/></hashcodes>";
    public static final String MIMETYPE = "application/vnd.etsi.asic-e+zip";
    public static final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/p12/sign_ECC_from_TEST_of_ESTEID2018.p12", "1234".toCharArray());

    public static HashcodeContainerFilesHolder getContainerFiles(byte[] container) throws IOException {
        try (SeekableInMemoryByteChannel byteChannel = new SeekableInMemoryByteChannel(container);
             ZipFile zipFile = new ZipFile(byteChannel)) {
            return buildHashcodeContainerFilesHolder(zipFile);
        }
    }

    private static HashcodeContainerFilesHolder buildHashcodeContainerFilesHolder(ZipFile zipFile) throws IOException {
        HashcodeContainerFilesHolder hashcodeContainer = new HashcodeContainerFilesHolder();
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String entryName = entry.getName();
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                if ("mimetype".equals(entryName)) {
                    hashcodeContainer.setMimeTypeContent(new String(inputStream.readAllBytes()));
                } else if ("META-INF/manifest.xml".equals(entryName)) {
                    hashcodeContainer.setManifestContent(new String(inputStream.readAllBytes()));
                } else if (HashcodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    hashcodeContainer.setHashcodesSha256Content(new String(inputStream.readAllBytes()));
                } else if (HashcodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    hashcodeContainer.setHashcodesSha512Content(new String(inputStream.readAllBytes()));
                }
            }
        }
        return hashcodeContainer;
    }

    public static InputStream getFileInputStream(String filePath) throws URISyntaxException, IOException {
        Path documentPath = getDocumentPath(filePath);
        return new ByteArrayInputStream(Files.readAllBytes(documentPath));
    }

    public static byte[] getFile(String filePath) throws URISyntaxException, IOException {
        Path documentPath = getDocumentPath(filePath);
        return Files.readAllBytes(documentPath);
    }

    private static Path getDocumentPath(String filePath) throws URISyntaxException {
        return Paths.get(TestUtil.class.getClassLoader().getResource(filePath).toURI());
    }

    public static Container createSignedContainer(SignatureProfile profile) {
        Container container = ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .withDataFile(new org.digidoc4j.DataFile("D0Zzjr7TcMXFLuCtlt7I9Fn7kBwspOKFIR7d+QO/FZg".getBytes(), "test.xml", "text/plain"))
                .build();
        SignatureBuilder builder = SignatureBuilder
                .aSignature(container)
                .withSignatureProfile(profile);
        org.digidoc4j.Signature signature = builder.withSignatureToken(pkcs12Esteid2018SignatureToken).invokeSigning();
        container.addSignature(signature);
        return container;
    }
}
