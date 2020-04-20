package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.exception.DuplicateDataFileException;
import eu.europa.esig.dss.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

public class HashcodesDataFileParser {

    private byte[] hashcodesDataFile;
    private Map<String, HashcodesEntry> entries = new HashMap<>();


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
        validateNotDuplicateFile(filePath);
        HashcodesEntry hashcodesEntry = new HashcodesEntry(hash, Integer.parseInt(size));
        entries.put(filePath, hashcodesEntry);
    }

    private void validateNotDuplicateFile(String filePath) {
        if (entries.containsKey(filePath))
            throw new DuplicateDataFileException("Hashcodes data file contains duplicate entry: " + filePath);
    }
}
