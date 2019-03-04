package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.service.signature.hashcode.HashCodesDataFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestUtil {
    public static final String MANIFEST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.etsi.asic-e+zip\"/><manifest:file-entry manifest:full-path=\"first datafile.txt\" manifest:media-type=\"application/octet-stream\"/><manifest:file-entry manifest:full-path=\"second datafile.txt\" manifest:media-type=\"application/octet-stream\"/></manifest:manifest>";
    public static final String HASHCODES_SHA256_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"SGotKr7DQfmpUTMp4p6jhumLKigNONEqC0pTySrYsms\" size=\"10\"/></hashcodes>";
    public static final String HASHCODES_SHA512_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"8dvW2xdYgT9ZEJBTibWXsP9H3LTOToBaQ6McE3BoPHjRnXvVOc/REszydaAMG4Pizt9RdsdKHbd94wO/E4Kfyw\" size=\"10\"/></hashcodes>";
    public static final String MIMETYPE = "application/vnd.etsi.asic-e+zip";

    public static DetachedDataFileContainerFilesHolder getContainerFiles(byte[] container) throws IOException {
        DetachedDataFileContainerFilesHolder hashCodeContainer = new DetachedDataFileContainerFilesHolder();
        ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(container));
        ZipEntry entry;
        while ((entry = zipStream.getNextEntry()) != null) {

            String entryName = entry.getName();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] byteBuff = new byte[4096];
                int bytesRead;
                while ((bytesRead = zipStream.read(byteBuff)) != -1) {
                    out.write(byteBuff, 0, bytesRead);
                }
                if ("mimetype".equals(entryName)) {
                    hashCodeContainer.setMimeTypeContent(out.toString());
                } else if ("META-INF/manifest.xml".equals(entryName)) {
                    hashCodeContainer.setManifestContent(out.toString());
                } else if (HashCodesDataFile.HASHCODES_SHA256.equals(entryName)) {
                    hashCodeContainer.setHashCodesSha256Content(out.toString());
                } else if (HashCodesDataFile.HASHCODES_SHA512.equals(entryName)) {
                    hashCodeContainer.setHashCodesSha512Content(out.toString());
                }
            }

            zipStream.closeEntry();
        }
        zipStream.close();
        return hashCodeContainer;
    }

    public static InputStream getFileInputStream(String filePath) throws URISyntaxException, IOException {
        Path documentPath = Paths.get(TestUtil.class.getClassLoader().getResource(filePath).toURI());
        return new ByteArrayInputStream(Files.readAllBytes(documentPath));

    }
}
