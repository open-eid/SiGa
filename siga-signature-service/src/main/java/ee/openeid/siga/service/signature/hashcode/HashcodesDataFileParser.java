package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.util.Base64Util;
import eu.europa.esig.dss.DomUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;

public class HashcodesDataFileParser {

    private byte[] hashcodesDataFile;
    private Map<String, HashcodesEntry> entries = new LinkedHashMap<>();


    public HashcodesDataFileParser(byte[] hashcodesDataFile) {
        this.hashcodesDataFile = hashcodesDataFile;
        loadHashcodesEntries();
    }

    public Map<String, HashcodesEntry> getEntries() {
        return entries;
    }

    private void loadHashcodesEntries() {
        Element root = DomUtils.buildDOM(hashcodesDataFile).getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            String nodeName = child.getLocalName();
            if ("file-entry".equals(nodeName)) {
                addFileEntry(child);
            }
            child = child.getNextSibling();
        }
    }

    private void addFileEntry(Node child) {
        NamedNodeMap attributes = child.getAttributes();
        String filePath = attributes.getNamedItem("full-path").getTextContent();
        String hash = attributes.getNamedItem("hash").getTextContent();
        String size = attributes.getNamedItem("size").getTextContent();
        validateParameters(filePath, hash, size);
        HashcodesEntry hashcodesEntry = new HashcodesEntry(hash, Integer.parseInt(size));
        entries.put(filePath, hashcodesEntry);
    }

    private void validateParameters(String filePath, String hash, String size) {
        if (filePath == null || hash == null || size == null) {
            throw new InvalidContainerException("Hashcodes data file is invalid");
        }
        validateSize(size);
        validateHash(hash);
        validateNotDuplicateFile(filePath);
    }

    private void validateHash(String hash) {
        if (!Base64Util.isValidBase64(hash) || (hash.length() != 44 && hash.length() != 88)) {
            throw new InvalidContainerException("Invalid data file hash");
        }
    }

    private void validateSize(String size) {
        if (!StringUtils.isNumeric(size) || Integer.parseInt(size) < 0) {
            throw new InvalidContainerException("Hashcodes data file invalid file size");
        }
    }

    private void validateNotDuplicateFile(String filePath) {
        if (entries.containsKey(filePath))
            throw new DuplicateDataFileException("Hashcodes data file contains duplicate entry: " + filePath);
    }
}
