package ee.openeid.siga.service.signature.test;

import ee.openeid.siga.service.signature.hashcode.AsicHashCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestUtil {
    public static final String MANIFEST_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.etsi.asic-e+zip\"/><manifest:file-entry manifest:full-path=\"first datafile.txt\" manifest:media-type=\"application/octet-stream\"/><manifest:file-entry manifest:full-path=\"second datafile.txt\" manifest:media-type=\"application/octet-stream\"/></manifest:manifest>";
    public static final String HASHCODES_SHA256_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"SHA256\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"SHA256\" size=\"10\"/></hashcodes>";
    public static final String HASHCODES_SHA512_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hashcodes><file-entry full-path=\"first datafile.txt\" hash=\"SHA512\" size=\"10\"/><file-entry full-path=\"second datafile.txt\" hash=\"SHA512\" size=\"10\"/></hashcodes>";
    public static final String MIMETYPE = "application/vnd.etsi.asic-e+zip";

    public static HashCodeContainerFilesHolder getContainerFiles(byte[] container) throws IOException {
        HashCodeContainerFilesHolder hashCodeContainer = new HashCodeContainerFilesHolder();
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
                } else if (AsicHashCode.HASHCODES_SHA256.equals(entryName)) {
                    hashCodeContainer.setHashCodesSha256Content(out.toString());
                } else if (AsicHashCode.HASHCODES_SHA512.equals(entryName)) {
                    hashCodeContainer.setHashCodesSha512Content(out.toString());
                }
            }

            zipStream.closeEntry();
        }
        zipStream.close();
        return hashCodeContainer;
    }
}
