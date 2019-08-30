package ee.openeid.siga.test.utils;

import io.restassured.path.xml.XmlPath;
import io.restassured.path.xml.config.XmlPathConfig;
import lombok.experimental.UtilityClass;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@UtilityClass
public final class ContainerUtil {

    public static final String MANIFEST_NAMESPACE_PREFIX = "manifest";
    public static final String MANIFEST_NAMESPACE_URL = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";


    public static byte[] extractEntryFromContainer(String entryPath, InputStream containerInputStream) {
        try (ZipInputStream zipInputStream = new ZipInputStream(containerInputStream)) {
            ZipEntry zipEntry;

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().equals(entryPath)) {
                    return extractEntryFromStream(zipEntry, zipInputStream);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read container", e);
        }
        throw new IllegalStateException("No entry " + entryPath + " found");
    }

    public static byte[] extractEntryFromContainer(String entryPath, byte[] containerBytes) {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(containerBytes)) {
            return extractEntryFromContainer(entryPath, byteArrayInputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read container", e);
        }
    }

    public static byte[] extractEntryFromContainer(String entryPath, String containerBase64String) {
        return extractEntryFromContainer(entryPath, Base64.getDecoder().decode(containerBase64String));
    }

    private static byte[] extractEntryFromStream(ZipEntry zipEntry, ZipInputStream zipInputStream) throws IOException {
        if (zipEntry.isDirectory()) {
            throw new IllegalStateException("Entry " + zipEntry.getName() + " is a directory");
        }
        return zipInputStream.readAllBytes();
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

}
