package ee.openeid.siga.test.utils;

import io.restassured.path.xml.XmlPath;
import io.restassured.path.xml.config.XmlPathConfig;
import lombok.experimental.UtilityClass;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

import static ee.openeid.siga.test.helper.TestData.HASHCODE_SHA512;

@UtilityClass
public final class ContainerUtil {

    public static final String MANIFEST_NAMESPACE_PREFIX = "manifest";
    public static final String MANIFEST_NAMESPACE_URL = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";

    public static byte[] extractEntryFromContainer(String entryPath, String containerBase64String) {
        return extractEntryFromContainer(entryPath, Base64.getDecoder().decode(containerBase64String));
    }

    public static byte[] extractEntryFromContainer(String entryPath, byte[] containerBytes) {
        try (SeekableInMemoryByteChannel byteChannel = new SeekableInMemoryByteChannel(containerBytes);
             ZipFile zipFile = new ZipFile(byteChannel)) {
            return extractEntryFromZipFile(entryPath, zipFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read container", e);
        }
    }

    private static byte[] extractEntryFromZipFile(String entryPath, ZipFile zipFile) throws IOException {
        ZipArchiveEntry zipEntry = Optional.ofNullable(zipFile.getEntry(entryPath))
                .orElseThrow(() -> new IllegalStateException("No entry " + entryPath + " found"));
        return extractEntryFromZipFile(zipEntry, zipFile);
    }

    private static byte[] extractEntryFromZipFile(ZipArchiveEntry zipEntry, ZipFile zipFile) throws IOException {
        if (zipEntry.isDirectory()) {
            throw new IllegalStateException("Entry " + zipEntry.getName() + " is a directory");
        }

        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            return inputStream.readAllBytes();
        }
    }

    public static XmlPath manifestAsXmlPath(String manifestXmlString) {
        return configureXmlPathForManifest(XmlPath.from(manifestXmlString));
    }

    public static XmlPath manifestAsXmlPath(byte[] manifestBytes) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(manifestBytes)) {
            return configureXmlPathForManifest(XmlPath.from(byteArrayInputStream));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read manifest", e);
        }
    }

    public static XmlPath manifestAsXmlPath(String entryPath, String containerBase64String) {
        return manifestAsXmlPath(extractEntryFromContainer(entryPath, containerBase64String));
    }

    public static XmlPath configureXmlPathForManifest(XmlPath xmlPath) {
        return xmlPath.using(XmlPathConfig.xmlPathConfig().declaredNamespace(MANIFEST_NAMESPACE_PREFIX, MANIFEST_NAMESPACE_URL));
    }

    public static XmlPath hashcodeDataFileAsXmlPath(String entryPath, String containerBase64String) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(extractEntryFromContainer(entryPath, containerBase64String))) {
            return XmlPath.from(byteArrayInputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read hashcode file", e);
        }
    }

    public static boolean getHashcodeSha512FilePresent(String container) {
        try {
            hashcodeDataFileAsXmlPath(HASHCODE_SHA512, container);
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("No entry META-INF/hashcodes-sha512.xml found")) {
                return false;
            }
        }
        return true;
    }

}
