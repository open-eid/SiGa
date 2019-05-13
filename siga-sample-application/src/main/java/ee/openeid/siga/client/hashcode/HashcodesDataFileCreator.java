package ee.openeid.siga.client.hashcode;

import eu.europa.esig.dss.DomUtils;
import lombok.SneakyThrows;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;

public class HashcodesDataFileCreator {

    @SneakyThrows
    public static HashcodeDataFile createHashcodeDataFile(String fileName, long fileSize, byte[] fileContent) {
        HashcodeDataFile df = new HashcodeDataFile();
        df.setFileName(fileName);
        df.setFileSize(Math.toIntExact(fileSize));
        MessageDigest digest256 = MessageDigest.getInstance("SHA-256");
        byte[] hash256 = digest256.digest(fileContent);
        MessageDigest digest512 = MessageDigest.getInstance("SHA-512");
        byte[] hash512 = digest512.digest(fileContent);
        df.setFileHashSha256(Base64.getEncoder().encodeToString(hash256));
        df.setFileHashSha512(Base64.getEncoder().encodeToString(hash512));
        return df;
    }

    @SneakyThrows
    public static HashcodeDataFile createHashcodeDataFile(ZipEntry entry, byte[] fileData) {
        return createHashcodeDataFile(entry.getName(), Math.toIntExact(entry.getSize()), fileData);
    }

    public static Map<String, HashcodesEntry> convertToHashcodeEntries(byte[] hashcodesDataFile) {
        Map<String, HashcodesEntry> entries = new HashMap<>();
        Element root = DomUtils.buildDOM(hashcodesDataFile).getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            String nodeName = child.getLocalName();
            if ("file-entry".equals(nodeName)) {
                NamedNodeMap attributes = child.getAttributes();
                String filePath = attributes.getNamedItem("full-path").getTextContent();
                String hash = attributes.getNamedItem("hash").getTextContent();
                String size = attributes.getNamedItem("size").getTextContent();
                HashcodesEntry hashcodesEntry = new HashcodesEntry(hash, Integer.parseInt(size));
                entries.put(filePath, hashcodesEntry);
            }
            child = child.getNextSibling();
        }
        return entries;
    }
}
